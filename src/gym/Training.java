package gym;

import java.sql.SQLException;
import java.util.Map;

/** One-on-one personal training sessions. */
final class Training {

    private static final String SELECT = """
            SELECT s.*, m.first_name || ' ' || m.last_name AS member_name,
                   t.first_name || ' ' || t.last_name AS trainer_name, r.name AS room_name
            FROM training_sessions s
            JOIN members m ON m.member_id = s.member_id
            JOIN trainers t ON t.trainer_id = s.trainer_id
            LEFT JOIN rooms r ON r.room_id = s.room_id""";

    private Training() {
    }

    static void register(WebServer server, Db db) {
        server.get("/api/training", r -> {
            boolean all = "all".equals(r.query("scope"));
            return db.query(SELECT + (all ? "" : " WHERE s.end_time >= ?") + " ORDER BY s.start_time",
                    all ? new Object[0] : new Object[] {Rules.now()});
        });

        server.post("/api/training", r -> save(db, r, null));
        server.put("/api/training/{id}", r -> save(db, r, r.id("id")));

        // Quick action: mark a session completed or cancelled without re-sending the whole record.
        server.post("/api/training/{id}/status", r -> db.tx(() -> {
            long id = r.id("id");
            String status = r.choice("status", "Status", null, "scheduled", "completed", "cancelled");
            if (status == null) {
                throw ApiException.badRequest("Status is required.");
            }
            Map<String, Object> session = get(db, id);
            if ("cancelled".equals(session.get("status")) && !"cancelled".equals(status)) {
                // Bringing a cancelled session back: the slot may have been given away meanwhile.
                checkAvailability(db, session, id);
            }
            db.update("UPDATE training_sessions SET status = ? WHERE session_id = ?", status, id);
            return get(db, id);
        }));

        server.delete("/api/training/{id}", r -> {
            if (db.update("DELETE FROM training_sessions WHERE session_id = ?", r.id("id")) == 0) {
                throw ApiException.notFound("Training session");
            }
            return Map.of("deleted", true);
        });
    }

    private static Map<String, Object> get(Db db, long id) throws SQLException {
        Map<String, Object> row = db.one(SELECT + " WHERE s.session_id = ?", id);
        if (row == null) {
            throw ApiException.notFound("Training session");
        }
        return row;
    }

    private static void checkAvailability(Db db, Map<String, Object> session, long id) throws SQLException {
        String start = (String) session.get("start_time");
        String end = (String) session.get("end_time");
        Rules.trainerFree(db, ((Number) session.get("trainer_id")).longValue(), start, end, "training", id);
        if (session.get("room_id") != null) {
            Rules.roomFree(db, ((Number) session.get("room_id")).longValue(), start, end, "training", id);
        }
    }

    private static Map<String, Object> save(Db db, Request r, Long id) throws Exception {
        long memberId = r.integer("member_id", "Member", true);
        long trainerId = r.integer("trainer_id", "Trainer", true);
        Long roomId = r.integer("room_id", "Room", false);
        String start = r.dateTime("start_time", "Start time", true);
        String end = r.dateTime("end_time", "End time", true);
        Rules.timeRange(start, end);
        String status = r.choice("status", "Status", "scheduled", "scheduled", "completed", "cancelled");
        String notes = r.text("notes");

        return db.tx(() -> {
            Rules.find(db, "trainers", "trainer_id", trainerId, "Trainer");
            if (roomId != null) {
                Rules.find(db, "rooms", "room_id", roomId, "Room");
            }
            Map<String, Object> existing = id == null ? null : get(db, id);
            boolean sameMember = existing != null && ((Number) existing.get("member_id")).longValue() == memberId;
            if (!sameMember) {
                Rules.activeMember(db, memberId); // editing an old session of a now-frozen member is still allowed
            }
            if (!"cancelled".equals(status)) {
                Rules.trainerFree(db, trainerId, start, end, "training", id);
                if (roomId != null) {
                    Rules.roomFree(db, roomId, start, end, "training", id);
                }
            }
            long sessionId;
            if (id == null) {
                sessionId = db.insert("""
                        INSERT INTO training_sessions (member_id, trainer_id, room_id, start_time, end_time, status, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""", memberId, trainerId, roomId, start, end, status, notes);
            } else {
                db.update("""
                        UPDATE training_sessions SET member_id = ?, trainer_id = ?, room_id = ?, start_time = ?,
                               end_time = ?, status = ?, notes = ?
                        WHERE session_id = ?""", memberId, trainerId, roomId, start, end, status, notes, id);
                sessionId = id;
            }
            return get(db, sessionId);
        });
    }
}
