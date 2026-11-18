package gym;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

/** Equipment inventory and its maintenance tickets. */
final class Equipment {

    private static final String SELECT = """
            SELECT e.*, r.name AS room_name,
                   (SELECT COUNT(*) FROM maintenance_logs l
                    WHERE l.equipment_id = e.equipment_id AND l.status <> 'completed') AS open_tickets
            FROM equipment e LEFT JOIN rooms r ON r.room_id = e.room_id""";

    private static final String TICKETS = """
            SELECT l.*, e.name AS equipment_name, e.status AS equipment_status, r.name AS room_name
            FROM maintenance_logs l
            JOIN equipment e ON e.equipment_id = l.equipment_id
            LEFT JOIN rooms r ON r.room_id = e.room_id""";

    private Equipment() {
    }

    static void register(WebServer server, Db db) {
        // ---- inventory
        server.get("/api/equipment", r -> db.query(SELECT + " ORDER BY e.category, e.name"));
        server.post("/api/equipment", r -> saveEquipment(db, r, null));
        server.put("/api/equipment/{id}", r -> saveEquipment(db, r, r.id("id")));

        // Deleting equipment also removes its maintenance history (ON DELETE CASCADE).
        server.delete("/api/equipment/{id}", r -> {
            if (db.update("DELETE FROM equipment WHERE equipment_id = ?", r.id("id")) == 0) {
                throw ApiException.notFound("Equipment");
            }
            return Map.of("deleted", true);
        });

        // ---- maintenance tickets
        server.get("/api/maintenance", r -> {
            boolean all = "all".equals(r.query("scope"));
            return db.query(TICKETS + (all ? "" : " WHERE l.status <> 'completed'")
                    + " ORDER BY l.status = 'completed', l.reported_date DESC, l.log_id DESC");
        });

        server.post("/api/maintenance", r -> saveTicket(db, r, null));
        server.put("/api/maintenance/{id}", r -> saveTicket(db, r, r.id("id")));

        server.delete("/api/maintenance/{id}", r -> db.tx(() -> {
            Map<String, Object> ticket = getTicket(db, r.id("id"));
            db.update("DELETE FROM maintenance_logs WHERE log_id = ?", ticket.get("log_id"));
            syncEquipment(db, ((Number) ticket.get("equipment_id")).longValue());
            return Map.of("deleted", true);
        }));
    }

    static java.util.List<Map<String, Object>> openTickets(Db db) throws SQLException {
        return db.query(TICKETS + " WHERE l.status <> 'completed' ORDER BY l.reported_date, l.log_id");
    }

    private static Map<String, Object> getEquipment(Db db, long id) throws SQLException {
        Map<String, Object> row = db.one(SELECT + " WHERE e.equipment_id = ?", id);
        if (row == null) {
            throw ApiException.notFound("Equipment");
        }
        return row;
    }

    private static Map<String, Object> getTicket(Db db, long id) throws SQLException {
        Map<String, Object> row = db.one(TICKETS + " WHERE l.log_id = ?", id);
        if (row == null) {
            throw ApiException.notFound("Maintenance ticket");
        }
        return row;
    }

    private static Map<String, Object> saveEquipment(Db db, Request r, Long id) throws Exception {
        String name = r.required("name", "Name");
        String category = r.required("category", "Category");
        String serial = r.text("serial_number");
        Long roomId = r.integer("room_id", "Location", false);
        String purchased = r.date("purchase_date", "Purchase date", false);
        String serviced = r.date("last_service_date", "Last service date", false);
        String status = r.choice("status", "Status", "operational",
                "operational", "out_of_order", "under_maintenance", "retired");

        return db.tx(() -> {
            if (roomId != null) {
                Rules.find(db, "rooms", "room_id", roomId, "Room");
            }
            long equipmentId;
            if (id == null) {
                equipmentId = db.insert("""
                        INSERT INTO equipment (name, category, serial_number, room_id, purchase_date, last_service_date, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""", name, category, serial, roomId, purchased, serviced, status);
            } else {
                getEquipment(db, id);
                db.update("""
                        UPDATE equipment SET name = ?, category = ?, serial_number = ?, room_id = ?, purchase_date = ?,
                               last_service_date = ?, status = ?
                        WHERE equipment_id = ?""", name, category, serial, roomId, purchased, serviced, status, id);
                equipmentId = id;
            }
            return getEquipment(db, equipmentId);
        });
    }

    private static Map<String, Object> saveTicket(Db db, Request r, Long id) throws Exception {
        long equipmentId = r.integer("equipment_id", "Equipment", true);
        String type = r.choice("type", "Type", "repair", "repair", "routine");
        String description = r.required("description", "Description");
        String status = r.choice("status", "Status", "open", "open", "in_progress", "completed");
        String today = LocalDate.now().toString();
        String reported = r.date("reported_date", "Reported date", false);
        String completedInput = r.date("completed_date", "Completed date", false);
        String technician = r.text("technician");
        Double cost = r.number("cost", "Cost", false);
        if (cost != null && cost < 0) {
            throw ApiException.badRequest("Cost can't be negative.");
        }
        String notes = r.text("notes");

        return db.tx(() -> {
            getEquipment(db, equipmentId);
            Map<String, Object> existing = id == null ? null : getTicket(db, id);
            String reportedDate = reported != null ? reported
                    : existing != null ? (String) existing.get("reported_date") : today;
            // A completed ticket always has a completion date; an unfinished one never does.
            String completedDate = !"completed".equals(status) ? null : completedInput != null ? completedInput : today;
            if (completedDate != null && completedDate.compareTo(reportedDate) < 0) {
                throw ApiException.badRequest("Completed date can't be before the reported date.");
            }
            long ticketId;
            if (id == null) {
                ticketId = db.insert("""
                        INSERT INTO maintenance_logs (equipment_id, type, description, status, reported_date,
                                                      completed_date, technician, cost, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        equipmentId, type, description, status, reportedDate, completedDate, technician, cost, notes);
            } else {
                db.update("""
                        UPDATE maintenance_logs SET equipment_id = ?, type = ?, description = ?, status = ?,
                               reported_date = ?, completed_date = ?, technician = ?, cost = ?, notes = ?
                        WHERE log_id = ?""",
                        equipmentId, type, description, status, reportedDate, completedDate, technician, cost, notes, id);
                long previous = ((Number) existing.get("equipment_id")).longValue();
                if (previous != equipmentId) {
                    syncEquipment(db, previous);
                }
                ticketId = id;
            }
            syncEquipment(db, equipmentId);
            return getTicket(db, ticketId);
        });
    }

    /**
     * Keeps an item's status in step with its tickets:
     * work in progress -> under maintenance; an open repair -> out of order; otherwise operational.
     * Routine (preventive) tickets that haven't started don't take equipment off the floor,
     * and retired equipment is left alone.
     */
    private static void syncEquipment(Db db, long equipmentId) throws SQLException {
        Map<String, Object> item = db.one("SELECT status FROM equipment WHERE equipment_id = ?", equipmentId);
        if (item == null || "retired".equals(item.get("status"))) {
            return;
        }
        String status;
        if (db.count("SELECT COUNT(*) FROM maintenance_logs WHERE equipment_id = ? AND status = 'in_progress'", equipmentId) > 0) {
            status = "under_maintenance";
        } else if (db.count("""
                SELECT COUNT(*) FROM maintenance_logs
                WHERE equipment_id = ? AND status = 'open' AND type = 'repair'""", equipmentId) > 0) {
            status = "out_of_order";
        } else {
            status = "operational";
        }
        db.update("""
                UPDATE equipment
                SET status = ?,
                    last_service_date = COALESCE(
                        (SELECT MAX(completed_date) FROM maintenance_logs
                         WHERE equipment_id = ? AND status = 'completed'),
                        last_service_date)
                WHERE equipment_id = ?""", status, equipmentId, equipmentId);
    }
}
