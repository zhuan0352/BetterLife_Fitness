package gym;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Member registration and membership records. */
final class Members {

    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final String SELECT = """
            SELECT m.*, p.name AS plan_name, p.monthly_fee
            FROM members m JOIN membership_plans p ON p.plan_id = m.plan_id""";

    private Members() {
    }

    static void register(WebServer server, Db db) {
        server.get("/api/plans", r -> db.query("SELECT * FROM membership_plans ORDER BY monthly_fee"));

        server.get("/api/members", r -> {
            StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
            List<Object> args = new ArrayList<>();
            String search = r.query("q");
            if (search != null) {
                sql.append(" AND (m.first_name || ' ' || m.last_name LIKE ? OR m.email LIKE ? OR m.phone LIKE ?)");
                String like = "%" + search + "%";
                args.addAll(List.of(like, like, like));
            }
            String status = r.query("status");
            if (status != null) {
                sql.append(" AND m.status = ?");
                args.add(status);
            }
            sql.append(" ORDER BY m.last_name, m.first_name");
            return db.query(sql.toString(), args.toArray());
        });

        // One member with everything they are signed up for.
        server.get("/api/members/{id}", r -> {
            long id = r.id("id");
            Map<String, Object> member = get(db, id);
            member.put("classes", db.query("""
                    SELECT c.class_id, c.name, c.start_time, c.end_time, c.status, r.name AS room_name
                    FROM class_enrollments e
                    JOIN classes c ON c.class_id = e.class_id
                    JOIN rooms r ON r.room_id = c.room_id
                    WHERE e.member_id = ? ORDER BY c.start_time DESC""", id));
            member.put("sessions", db.query("""
                    SELECT s.session_id, s.start_time, s.end_time, s.status,
                           t.first_name || ' ' || t.last_name AS trainer_name
                    FROM training_sessions s JOIN trainers t ON t.trainer_id = s.trainer_id
                    WHERE s.member_id = ? ORDER BY s.start_time DESC""", id));
            return member;
        });

        server.post("/api/members", r -> save(db, r, null));
        server.put("/api/members/{id}", r -> save(db, r, r.id("id")));

        // Deleting a member also removes their enrollments, sessions and bookings (ON DELETE CASCADE).
        server.delete("/api/members/{id}", r -> {
            if (db.update("DELETE FROM members WHERE member_id = ?", r.id("id")) == 0) {
                throw ApiException.notFound("Member");
            }
            return Map.of("deleted", true);
        });
    }

    private static Map<String, Object> get(Db db, long id) throws SQLException {
        Map<String, Object> member = db.one(SELECT + " WHERE m.member_id = ?", id);
        if (member == null) {
            throw ApiException.notFound("Member");
        }
        return member;
    }

    private static Map<String, Object> save(Db db, Request r, Long id) throws Exception {
        String first = r.required("first_name", "First name");
        String last = r.required("last_name", "Last name");
        String email = r.required("email", "Email");
        if (!EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("Email address doesn't look valid.");
        }
        String phone = r.text("phone");
        String birth = r.date("date_of_birth", "Date of birth", false);
        if (birth != null && birth.compareTo(LocalDate.now().toString()) > 0) {
            throw ApiException.badRequest("Date of birth can't be in the future.");
        }
        String emergency = r.text("emergency_contact");
        long planId = r.integer("plan_id", "Membership plan", true);
        String joined = r.date("join_date", "Join date", false);
        String status = r.choice("status", "Status", "active", "active", "frozen", "cancelled");

        return db.tx(() -> {
            Rules.find(db, "membership_plans", "plan_id", planId, "Membership plan");
            long memberId;
            if (id == null) {
                memberId = db.insert("""
                        INSERT INTO members (first_name, last_name, email, phone, date_of_birth, emergency_contact,
                                             plan_id, join_date, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, date('now', 'localtime')), ?)""",
                        first, last, email, phone, birth, emergency, planId, joined, status);
            } else {
                get(db, id);
                db.update("""
                        UPDATE members SET first_name = ?, last_name = ?, email = ?, phone = ?, date_of_birth = ?,
                               emergency_contact = ?, plan_id = ?, join_date = COALESCE(?, join_date), status = ?
                        WHERE member_id = ?""",
                        first, last, email, phone, birth, emergency, planId, joined, status, id);
                memberId = id;
            }
            return get(db, memberId);
        });
    }
}
