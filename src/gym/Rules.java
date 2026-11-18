package gym;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Business rules shared by classes, personal training and room bookings. */
final class Rules {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private Rules() {
    }

    /** The current local time in the same text format the database stores. */
    static String now() {
        return LocalDateTime.now().format(Request.DATE_TIME);
    }

    static void timeRange(String start, String end) {
        if (start.compareTo(end) >= 0) {
            throw ApiException.badRequest("End time must be after the start time.");
        }
    }

    /** Loads a row by primary key or fails with "X not found". Table and column come from code, never user input. */
    static Map<String, Object> find(Db db, String table, String idColumn, long id, String label) throws SQLException {
        Map<String, Object> row = db.one("SELECT * FROM " + table + " WHERE " + idColumn + " = ?", id);
        if (row == null) {
            throw ApiException.notFound(label);
        }
        return row;
    }

    /** Only members in good standing can be enrolled, trained or given a room. */
    static Map<String, Object> activeMember(Db db, long memberId) throws SQLException {
        Map<String, Object> member = find(db, "members", "member_id", memberId, "Member");
        if (!"active".equals(member.get("status"))) {
            throw ApiException.conflict(member.get("first_name") + " " + member.get("last_name")
                    + "'s membership is " + member.get("status") + ". Reactivate it first.");
        }
        return member;
    }

    /**
     * Fails if the room already has anything overlapping [start, end).
     * kind/refId identify the record being edited so it does not clash with itself.
     */
    static void roomFree(Db db, long roomId, String start, String end, String kind, Long refId) throws SQLException {
        Map<String, Object> clash = db.one("""
                SELECT s.title, s.start_time, s.end_time, r.name AS room_name
                FROM schedule s JOIN rooms r ON r.room_id = s.room_id
                WHERE s.room_id = ? AND s.start_time < ? AND s.end_time > ?
                  AND NOT (s.kind = ? AND s.ref_id = ?)
                ORDER BY s.start_time LIMIT 1""",
                roomId, end, start, kind, refId == null ? -1L : refId);
        if (clash != null) {
            throw ApiException.conflict(clash.get("room_name") + " is already in use then: \"" + clash.get("title")
                    + "\" (" + span(clash) + ").");
        }
    }

    /** Fails if the trainer is already teaching a class or session overlapping [start, end). */
    static void trainerFree(Db db, long trainerId, String start, String end, String kind, Long refId) throws SQLException {
        Map<String, Object> clash = db.one("""
                SELECT s.title, s.start_time, s.end_time, t.first_name || ' ' || t.last_name AS trainer_name
                FROM schedule s JOIN trainers t ON t.trainer_id = s.trainer_id
                WHERE s.trainer_id = ? AND s.start_time < ? AND s.end_time > ?
                  AND NOT (s.kind = ? AND s.ref_id = ?)
                ORDER BY s.start_time LIMIT 1""",
                trainerId, end, start, kind, refId == null ? -1L : refId);
        if (clash != null) {
            throw ApiException.conflict(clash.get("trainer_name") + " is already busy then: \"" + clash.get("title")
                    + "\" (" + span(clash) + ").");
        }
    }

    private static String span(Map<String, Object> row) {
        LocalDateTime start = LocalDateTime.parse((String) row.get("start_time"));
        LocalDateTime end = LocalDateTime.parse((String) row.get("end_time"));
        return start.format(DAY) + "–" + end.format(TIME);
    }
}
