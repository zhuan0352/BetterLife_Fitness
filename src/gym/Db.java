package gym;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin JDBC wrapper around a single SQLite connection.
 * Every statement is a PreparedStatement with bound parameters, and rows come
 * back as ordered maps (column name -> value) ready to be written as JSON.
 */
final class Db {

    @FunctionalInterface
    interface Work<T> {
        T run() throws Exception;
    }

    private final Connection conn;

    Db(String file) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON"); // SQLite leaves FK enforcement off by default
        }
    }

    /** Creates the tables if needed and loads sample data into a brand-new database. */
    void init(Path sqlDir) throws SQLException, IOException {
        runScript(sqlDir.resolve("schema.sql"));
        if (count("SELECT COUNT(*) FROM membership_plans") == 0) {
            try {
                tx(() -> {
                    runScript(sqlDir.resolve("seed.sql"));
                    return null;
                });
            } catch (SQLException | IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    synchronized List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    /** First row of the result, or null when there is none. */
    Map<String, Object> one(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** For queries that return a single number, such as SELECT COUNT(*). */
    long count(String sql, Object... params) throws SQLException {
        Map<String, Object> row = one(sql, params);
        Object value = row == null ? null : row.values().iterator().next();
        return value == null ? 0 : ((Number) value).longValue();
    }

    /** Runs an INSERT / UPDATE / DELETE and returns the number of rows changed. */
    synchronized int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(sql, params)) {
            return ps.executeUpdate();
        }
    }

    /** Runs an INSERT and returns the new row's primary key. */
    synchronized long insert(String sql, Object... params) throws SQLException {
        update(sql, params);
        return count("SELECT last_insert_rowid()");
    }

    /** Runs the work in one transaction: everything commits, or nothing does. */
    synchronized <T> T tx(Work<T> work) throws Exception {
        if (!conn.getAutoCommit()) {
            return work.run(); // already inside a transaction
        }
        conn.setAutoCommit(false);
        try {
            T result = work.run();
            conn.commit();
            return result;
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    /** Executes a .sql file: comment lines are dropped, statements are split on ';'. */
    private synchronized void runScript(Path file) throws SQLException, IOException {
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(file)) {
            if (!line.stripLeading().startsWith("--")) {
                sql.append(line).append('\n');
            }
        }
        try (Statement st = conn.createStatement()) {
            for (String statement : sql.toString().split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }
        }
    }
}
