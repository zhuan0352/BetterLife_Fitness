package gym;

import java.sql.SQLException;
import java.util.Map;

/** Group class scheduling and enrollment. */
final class Classes {

    private static final String SELECT = """
            SELECT c.*, t.first_name || ' ' || t.last_name AS trainer_name, r.name AS room_name,
                   (SELECT COUNT(*) FROM class_enrollments e WHERE e.class_id = c.class_id) AS enrolled
            FROM classes c
            JOIN trainers t ON t.trainer_id = c.trainer_id
            JOIN rooms r ON r.room_id = c.room_id""";

    private Classes() {
    }

    static void register(WebServer server, Db db) {
        server.get("/api/classes", r -> {
            boolean all = "all".equals(r.query("scope"));
            return db.query(SELECT + (all ? "" : " WHERE c.end_time >= ?") + " ORDER BY c.start_time",
                    all ? new Object[0] : new Object[] {Rules.now()});
        });

        server.post("/api/classes", r -> save(db, r, null));
        server.put("/api/classes/{id}", r -> save(db, r, r.id("id")));

        server.delete("/api/classes/{id}", r -> {
            if (db.update("DELETE FROM classes WHERE class_id = ?", r.id("id")) == 0) {
                throw ApiException.notFound("Class");
            }
            return Map.of("deleted", true);
        });

        // ---- enrollment
        server.get("/api/classes/{id}/enrollments", r -> {
            long id = r.id("id");
            get(db, id);
            return db.query("""
                    SELECT m.member_id, m.first_name, m.last_name, m.email, e.enrolled_at
                    FROM class_enrollments e JOIN members m ON m.member_id = e.member_id
                    WHERE e.class_id = ? ORDER BY m.last_name, m.first_name""", id);
        });

        // Checking the spot count and taking the spot happen in one transaction,
        // so two front-desk screens can't both grab the last place.
        server.post("/api/classes/{id}/enrollments", r -> db.tx(() -> {
            long classId = r.id("id");
            long memberId = r.integer("member_id", "Member", true);
            Map<String, Object> c = get(db, classId);
            if (!"scheduled".equals(c.get("status"))) {
                throw ApiException.conflict("This class has been cancelled.");
            }
            if (((String) c.get("end_time")).compareTo(Rules.now()) < 0) {
                throw ApiException.conflict("This class has already finished.");
            }
            Rules.activeMember(db, memberId);
            if (db.one("SELECT 1 FROM class_enrollments WHERE class_id = ? AND member_id = ?", classId, memberId) != null) {
                throw ApiException.conflict("That member is already enrolled in this class.");
            }
            long enrolled = ((Number) c.get("enrolled")).longValue();
            long capacity = ((Number) c.get("capacity")).longValue();
            if (enrolled >= capacity) {
                throw ApiException.conflict("This class is full (" + enrolled + " of " + capacity + " spots taken).");
            }
            db.update("INSERT INTO class_enrollments (class_id, member_id) VALUES (?, ?)", classId, memberId);
            return get(db, classId);
        }));

        server.delete("/api/classes/{id}/enrollments/{memberId}", r -> {
            int removed = db.update("DELETE FROM class_enrollments WHERE class_id = ? AND member_id = ?",
                    r.id("id"), r.id("memberId"));
            if (removed == 0) {
                throw ApiException.notFound("Enrollment");
            }
            return Map.of("deleted", true);
        });
    }

    private static Map<String, Object> get(Db db, long id) throws SQLException {
        Map<String, Object> row = db.one(SELECT + " WHERE c.class_id = ?", id);
        if (row == null) {
            throw ApiException.notFound("Class");
        }
        return row;
    }

    private static Map<String, Object> save(Db db, Request r, Long id) throws Exception {
        String name = r.required("name", "Class name");
        String description = r.text("description");
        long trainerId = r.integer("trainer_id", "Trainer", true);
        long roomId = r.integer("room_id", "Room", true);
        String start = r.dateTime("start_time", "Start time", true);
        String end = r.dateTime("end_time", "End time", true);
        Rules.timeRange(start, end);
        long capacity = r.integer("capacity", "Capacity", true);
        if (capacity < 1) {
            throw ApiException.badRequest("Capacity must be at least 1.");
        }
        String status = r.choice("status", "Status", "scheduled", "scheduled", "cancelled");

        return db.tx(() -> {
            Rules.find(db, "trainers", "trainer_id", trainerId, "Trainer");
            Map<String, Object> room = Rules.find(db, "rooms", "room_id", roomId, "Room");
            long roomCapacity = ((Number) room.get("capacity")).longValue();
            if (capacity > roomCapacity) {
                throw ApiException.badRequest("Capacity (" + capacity + ") is more than " + room.get("name")
                        + " can hold (" + roomCapacity + ").");
            }
            if (id != null) {
                long enrolled = ((Number) get(db, id).get("enrolled")).longValue();
                if (capacity < enrolled) {
                    throw ApiException.conflict(enrolled + " members are already enrolled, so capacity can't go below that.");
                }
            }
            if (!"cancelled".equals(status)) {
                Rules.roomFree(db, roomId, start, end, "class", id);
                Rules.trainerFree(db, trainerId, start, end, "class", id);
            }
            long classId;
            if (id == null) {
                classId = db.insert("""
                        INSERT INTO classes (name, description, trainer_id, room_id, start_time, end_time, capacity, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                        name, description, trainerId, roomId, start, end, capacity, status);
            } else {
                db.update("""
                        UPDATE classes SET name = ?, description = ?, trainer_id = ?, room_id = ?, start_time = ?,
                               end_time = ?, capacity = ?, status = ?
                        WHERE class_id = ?""",
                        name, description, trainerId, roomId, start, end, capacity, status, id);
                classId = id;
            }
            return get(db, classId);
        });
    }
}
