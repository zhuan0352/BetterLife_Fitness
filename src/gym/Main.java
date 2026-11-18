package gym;

import java.nio.file.Path;

/**
 * Fitness Center Manager: a Java web application backed by SQLite over JDBC.
 * Run it with ./run.sh, then open the printed address in a browser.
 *
 * Settings (environment variables):
 *   PORT       port to listen on (default 8080; Render sets this)
 *   HOST       address to listen on (default: localhost only; the Docker image sets 0.0.0.0)
 *   GYM_DB     database file (default fitness_center.db)
 *   DEMO_MODE  "true" shows a banner telling visitors this is a public demo with sample data
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String host = System.getenv("HOST");
        String dbFile = System.getenv().getOrDefault("GYM_DB", "fitness_center.db");
        boolean demo = "true".equalsIgnoreCase(System.getenv("DEMO_MODE"));

        Db db = new Db(dbFile);
        db.init(Path.of("sql"));

        WebServer server = new WebServer(host, port, Path.of("web"));
        // Tells the page whether to show the demo banner; also used as the health check.
        server.get("/api/info", r -> java.util.Map.of("demo", demo));
        Dashboard.register(server, db);
        Members.register(server, db);
        Trainers.register(server, db);
        Classes.register(server, db);
        Training.register(server, db);
        Rooms.register(server, db);
        Equipment.register(server, db);
        server.start();

        System.out.println("Fitness Center Manager is running.");
        System.out.println("  Database: " + Path.of(dbFile).toAbsolutePath());
        System.out.println("  Open:     http://localhost:" + port + (host == null ? "" : "  (listening on " + host + ")"));
        System.out.println("Press Ctrl+C to stop.");
    }
}
