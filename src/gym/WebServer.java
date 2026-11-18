package gym;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small HTTP layer on the JDK's built-in server: serves the web page from web/
 * and routes /api/... calls to handlers that return JSON.
 */
final class WebServer {

    @FunctionalInterface
    interface Handler {
        Object handle(Request request) throws Exception;
    }

    private record Route(String method, String[] parts, Handler handler) {
    }

    /** Friendly messages for UNIQUE constraints, keyed by "table.column" as SQLite reports them. */
    private static final Map<String, String> DUPLICATES = Map.of(
            "members.email", "A member with that email address already exists.",
            "trainers.email", "A trainer with that email address already exists.",
            "rooms.name", "A room with that name already exists.",
            "equipment.serial_number", "Another piece of equipment already has that serial number.",
            "membership_plans.name", "A plan with that name already exists.",
            "class_enrollments.class_id", "That member is already enrolled in this class.");

    private final List<Route> routes = new ArrayList<>();
    private final HttpServer http;
    private final Path webRoot;

    /** Largest request body accepted; the public demo shouldn't be able to fill the disk. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    /**
     * host == null listens on loopback only (the default when run locally: there is no login).
     * A deployment passes "0.0.0.0" so the hosting platform can route traffic in.
     */
    WebServer(String host, int port, Path webRoot) throws IOException {
        this.webRoot = webRoot.toAbsolutePath().normalize();
        InetSocketAddress address = host == null
                ? new InetSocketAddress(InetAddress.getLoopbackAddress(), port)
                : new InetSocketAddress(host, port);
        http = HttpServer.create(address, 0);
        http.createContext("/", this::dispatch);
    }

    void get(String pattern, Handler handler) {
        routes.add(new Route("GET", pattern.split("/"), handler));
    }

    void post(String pattern, Handler handler) {
        routes.add(new Route("POST", pattern.split("/"), handler));
    }

    void put(String pattern, Handler handler) {
        routes.add(new Route("PUT", pattern.split("/"), handler));
    }

    void delete(String pattern, Handler handler) {
        routes.add(new Route("DELETE", pattern.split("/"), handler));
    }

    void start() {
        http.start();
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else {
                serveFile(exchange, path);
            }
        } catch (ApiException e) {
            sendError(exchange, e.status, e.getMessage());
        } catch (SQLException e) {
            sendError(exchange, 409, explain(e));
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Something went wrong on the server. Check the console for details.");
        } finally {
            exchange.close();
        }
    }

    // ------------------------------------------------------------------ API

    private void handleApi(HttpExchange exchange, String path) throws Exception {
        String[] parts = path.split("/");
        boolean pathExists = false;
        for (Route route : routes) {
            Map<String, String> params = match(route.parts, parts);
            if (params == null) {
                continue;
            }
            pathExists = true;
            if (!route.method.equals(exchange.getRequestMethod())) {
                continue;
            }
            Request request = new Request(params, parseQuery(exchange.getRequestURI().getRawQuery()), readBody(exchange));
            send(exchange, 200, "application/json; charset=utf-8",
                    Json.stringify(route.handler.handle(request)).getBytes(StandardCharsets.UTF_8));
            return;
        }
        throw new ApiException(pathExists ? 405 : 404, pathExists ? "Method not allowed." : "Not found.");
    }

    /** Matches "/api/members/{id}" against "/api/members/7"; returns the captured values or null. */
    private static Map<String, String> match(String[] pattern, String[] actual) {
        if (pattern.length != actual.length) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                params.put(p.substring(1, p.length() - 1), actual[i]);
            } else if (!p.equals(actual[i])) {
                return null;
            }
        }
        return params;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            query.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "Request is too large.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return new LinkedHashMap<>();
        }
        if (Json.parse(text) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw ApiException.badRequest("Request body must be a JSON object.");
    }

    /** Turns a database constraint failure into a sentence a staff member can act on. */
    private static String explain(SQLException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("UNIQUE constraint failed")) {
            for (Map.Entry<String, String> entry : DUPLICATES.entrySet()) {
                if (message.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return "That value is already in use.";
        }
        if (message.contains("FOREIGN KEY constraint failed")) {
            return "This record is linked to other records, so the change was not made.";
        }
        if (message.contains("NOT NULL constraint failed") || message.contains("CHECK constraint failed")) {
            return "One of the values is missing or not allowed.";
        }
        e.printStackTrace();
        return "The database rejected the change.";
    }

    // --------------------------------------------------------- static files

    private void serveFile(HttpExchange exchange, String path) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            throw new ApiException(405, "Method not allowed.");
        }
        Path file = webRoot.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "text/javascript; charset=utf-8"
                : name.endsWith(".svg") ? "image/svg+xml"
                : "application/octet-stream";
        send(exchange, 200, type, Files.readAllBytes(file));
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        send(exchange, status, "application/json; charset=utf-8", Json.stringify(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
