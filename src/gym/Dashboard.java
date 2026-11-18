package gym;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Headline numbers and today's calendar for the home screen. */
final class Dashboard {

    private Dashboard() {
    }

    static void register(WebServer server, Db db) {
        server.get("/api/dashboard", r -> {
            String now = Rules.now();
            String weekAhead = LocalDateTime.now().plusDays(7).format(Request.DATE_TIME);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("active_members", db.count("SELECT COUNT(*) FROM members WHERE status = 'active'"));
            stats.put("total_members", db.count("SELECT COUNT(*) FROM members"));
            stats.put("classes_this_week", db.count("""
                    SELECT COUNT(*) FROM classes
                    WHERE status = 'scheduled' AND end_time >= ? AND start_time < ?""", now, weekAhead));
            stats.put("sessions_this_week", db.count("""
                    SELECT COUNT(*) FROM training_sessions
                    WHERE status = 'scheduled' AND end_time >= ? AND start_time < ?""", now, weekAhead));
            stats.put("open_tickets", db.count("SELECT COUNT(*) FROM maintenance_logs WHERE status <> 'completed'"));
            stats.put("equipment_down", db.count("""
                    SELECT COUNT(*) FROM equipment WHERE status IN ('out_of_order', 'under_maintenance')"""));
            stats.put("monthly_revenue", db.one("""
                    SELECT COALESCE(SUM(p.monthly_fee), 0) AS total
                    FROM members m JOIN membership_plans p ON p.plan_id = m.plan_id
                    WHERE m.status = 'active'""").get("total"));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stats", stats);
            result.put("today", Rooms.schedule(db, LocalDate.now().toString(), null));
            result.put("tickets", Equipment.openTickets(db));
            return result;
        });
    }
}
