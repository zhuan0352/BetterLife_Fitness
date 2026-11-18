package gym;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rooms, ad-hoc room bookings, and the combined room calendar. */
final class Rooms {

    private static final String BOOKINGS = """
            SELECT b.*, r.name AS room_name, m.first_name || ' ' || m.last_name AS member_name
            FROM room_bookings b
            JOIN rooms r ON r.room_id = b.room_id
            LEFT JOIN members m ON m.member_id = b.member_id""";

    private Rooms() {
    }

    static void register(WebServer server, Db db) {
        // ---- rooms
        server.get("/api/rooms", r -> db.query("""
                SELECT r.*, (SELECT COUNT(*) FROM equipment e WHERE e.room_id = r.room_id) AS equipment_count
                FROM rooms r ORDER BY r.name"""));

        server.post("/api/rooms", r -> saveRoom(db, r, null));
        server.put("/api/rooms/{id}", r -> saveRoom(db, r, r.id("id")));

        server.delete("/api/rooms/{id}", r -> db.tx(() -> {
            long id = r.id("id");
            Rules.find(db, "rooms", "room_id", id, "Room");
            long linked = db.count("SELECT COUNT(*) FROM classes WHERE room_id = ?", id)
                    + db.count("SELECT COUNT(*) FROM training_sessions WHERE room_id = ?", id)
                    + db.count("SELECT COUNT(*) FROM room_bookings WHERE room_id = ?", id);
            if (linked > 0) {
                throw ApiException.conflict("This room still has " + linked
                        + " class(es), session(s) or booking(s) on record. Move or delete those first.");
            }
            db.update("DELETE FROM rooms WHERE room_id = ?", id); // equipment in it becomes "unassigned"
            return Map.of("deleted", true);
        }));

        // ---- everything happening in the rooms on one day (classes, training, bookings)
        server.get("/api/schedule", r -> {
            String date = r.query("date");
            String room = r.query("room_id");
            Long roomId = null;
            try {
                date = date == null ? LocalDate.now().toString() : LocalDate.parse(date).toString();
                roomId = room == null ? null : Long.valueOf(room);
            } catch (RuntimeException e) {
                throw ApiException.badRequest("Invalid date or room.");
            }
            return schedule(db, date, roomId);
        });

        // ---- ad-hoc bookings
        server.get("/api/bookings", r -> {
            boolean all = "all".equals(r.query("scope"));
            return db.query(BOOKINGS + (all ? "" : " WHERE b.end_time >= ?") + " ORDER BY b.start_time",
                    all ? new Object[0] : new Object[] {Rules.now()});
        });

        server.post("/api/bookings", r -> saveBooking(db, r, null));
        server.put("/api/bookings/{id}", r -> saveBooking(db, r, r.id("id")));

        server.delete("/api/bookings/{id}", r -> {
            if (db.update("DELETE FROM room_bookings WHERE booking_id = ?", r.id("id")) == 0) {
                throw ApiException.notFound("Booking");
            }
            return Map.of("deleted", true);
        });
    }

    static List<Map<String, Object>> schedule(Db db, String date, Long roomId) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT s.kind, s.ref_id, s.title, s.start_time, s.end_time, s.room_id,
                       r.name AS room_name, t.first_name || ' ' || t.last_name AS trainer_name
                FROM schedule s
                LEFT JOIN rooms r ON r.room_id = s.room_id
                LEFT JOIN trainers t ON t.trainer_id = s.trainer_id
                WHERE substr(s.start_time, 1, 10) = ?""");
        List<Object> args = new ArrayList<>(List.of(date));
        if (roomId != null) {
            sql.append(" AND s.room_id = ?");
            args.add(roomId);
        }
        sql.append(" ORDER BY s.start_time, r.name");
        return db.query(sql.toString(), args.toArray());
    }

    private static Map<String, Object> saveRoom(Db db, Request r, Long id) throws Exception {
        String name = r.required("name", "Room name");
        String type = r.required("room_type", "Room type");
        long capacity = r.integer("capacity", "Capacity", true);
        if (capacity < 1) {
            throw ApiException.badRequest("Capacity must be at least 1.");
        }
        return db.tx(() -> {
            long roomId;
            if (id == null) {
                roomId = db.insert("INSERT INTO rooms (name, room_type, capacity) VALUES (?, ?, ?)", name, type, capacity);
            } else {
                Rules.find(db, "rooms", "room_id", id, "Room");
                long largest = db.count("""
                        SELECT COALESCE(MAX(capacity), 0) FROM classes
                        WHERE room_id = ? AND status = 'scheduled' AND end_time >= ?""", id, Rules.now());
                if (capacity < largest) {
                    throw ApiException.conflict("An upcoming class in this room is set up for " + largest
                            + " people. Reduce that class first.");
                }
                db.update("UPDATE rooms SET name = ?, room_type = ?, capacity = ? WHERE room_id = ?",
                        name, type, capacity, id);
                roomId = id;
            }
            return Rules.find(db, "rooms", "room_id", roomId, "Room");
        });
    }

    private static Map<String, Object> saveBooking(Db db, Request r, Long id) throws Exception {
        long roomId = r.integer("room_id", "Room", true);
        Long memberId = r.integer("member_id", "Member", false);
        String purpose = r.required("purpose", "Purpose");
        String start = r.dateTime("start_time", "Start time", true);
        String end = r.dateTime("end_time", "End time", true);
        Rules.timeRange(start, end);

        return db.tx(() -> {
            Rules.find(db, "rooms", "room_id", roomId, "Room");
            if (memberId != null) {
                Rules.activeMember(db, memberId);
            }
            Rules.roomFree(db, roomId, start, end, "booking", id);
            long bookingId;
            if (id == null) {
                bookingId = db.insert("""
                        INSERT INTO room_bookings (room_id, member_id, purpose, start_time, end_time)
                        VALUES (?, ?, ?, ?, ?)""", roomId, memberId, purpose, start, end);
            } else {
                Rules.find(db, "room_bookings", "booking_id", id, "Booking");
                db.update("""
                        UPDATE room_bookings SET room_id = ?, member_id = ?, purpose = ?, start_time = ?, end_time = ?
                        WHERE booking_id = ?""", roomId, memberId, purpose, start, end, id);
                bookingId = id;
            }
            return db.one(BOOKINGS + " WHERE b.booking_id = ?", bookingId);
        });
    }
}
