package gym;

import java.util.Map;

/** Trainer records. */
final class Trainers {

    private Trainers() {
    }

    static void register(WebServer server, Db db) {
        server.get("/api/trainers", r -> db.query("""
                SELECT t.*,
                       (SELECT COUNT(*) FROM schedule s
                        WHERE s.trainer_id = t.trainer_id
                          AND s.end_time >= strftime('%Y-%m-%dT%H:%M', 'now', 'localtime')) AS upcoming
                FROM trainers t ORDER BY t.last_name, t.first_name"""));

        server.post("/api/trainers", r -> save(db, r, null));
        server.put("/api/trainers/{id}", r -> save(db, r, r.id("id")));

        server.delete("/api/trainers/{id}", r -> db.tx(() -> {
            long id = r.id("id");
            Rules.find(db, "trainers", "trainer_id", id, "Trainer");
            long linked = db.count("SELECT COUNT(*) FROM classes WHERE trainer_id = ?", id)
                    + db.count("SELECT COUNT(*) FROM training_sessions WHERE trainer_id = ?", id);
            if (linked > 0) {
                throw ApiException.conflict("This trainer still has " + linked
                        + " class(es) or training session(s) on record. Reassign or delete those first.");
            }
            db.update("DELETE FROM trainers WHERE trainer_id = ?", id);
            return Map.of("deleted", true);
        }));
    }

    private static Map<String, Object> save(Db db, Request r, Long id) throws Exception {
        String first = r.required("first_name", "First name");
        String last = r.required("last_name", "Last name");
        String email = r.required("email", "Email");
        if (!Members.EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("Email address doesn't look valid.");
        }
        String phone = r.text("phone");
        String specialty = r.text("specialty");

        return db.tx(() -> {
            long trainerId;
            if (id == null) {
                trainerId = db.insert(
                        "INSERT INTO trainers (first_name, last_name, email, phone, specialty) VALUES (?, ?, ?, ?, ?)",
                        first, last, email, phone, specialty);
            } else {
                Rules.find(db, "trainers", "trainer_id", id, "Trainer");
                db.update("""
                        UPDATE trainers SET first_name = ?, last_name = ?, email = ?, phone = ?, specialty = ?
                        WHERE trainer_id = ?""", first, last, email, phone, specialty, id);
                trainerId = id;
            }
            return Rules.find(db, "trainers", "trainer_id", trainerId, "Trainer");
        });
    }
}
