package gym;

import java.nio.file.Path;

/**
 * Fitness Center Manager: a Java web application backed by SQLite over JDBC.
 * Run it with ./run.sh, then open the printed address in a browser.
 *
 * Settings (environment variables): PORT (default 8080), GYM_DB (default fitness_center.db).
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String dbFile = System.getenv().getOrDefault("GYM_DB", "fitness_center.db");

        Db db = new Db(dbFile);
        db.init(Path.of("sql"));

        WebServer server = new WebServer(port, Path.of("web"));
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
        System.out.println("  Open:     http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }
}
