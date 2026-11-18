package gym;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;

/** One API call: path parameters, query string and JSON body, with input validation helpers. */
final class Request {

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final Map<String, String> path;
    private final Map<String, String> query;
    private final Map<String, Object> body;

    Request(Map<String, String> path, Map<String, String> query, Map<String, Object> body) {
        this.path = path;
        this.query = query;
        this.body = body;
    }

    /** A numeric id from the URL, e.g. {id} in /api/members/{id}. */
    long id(String name) {
        try {
            return Long.parseLong(path.get(name));
        } catch (NumberFormatException e) {
            throw new ApiException(404, "Not found.");
        }
    }

    /** A query-string value, or null when absent or blank. */
    String query(String key) {
        String value = query.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    static final int MAX_TEXT = 500;

    /** A body field as trimmed text, or null when absent or blank. */
    String text(String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.length() > MAX_TEXT) {
            throw ApiException.badRequest("\"" + key.replace('_', ' ') + "\" is too long (max " + MAX_TEXT + " characters).");
        }
        return text.isEmpty() ? null : text;
    }

    String required(String key, String label) {
        String value = text(key);
        if (value == null) {
            throw ApiException.badRequest(label + " is required.");
        }
        return value;
    }

    /** A whole number; forms send these as text, so both 5 and "5" are accepted. */
    Long integer(String key, String label, boolean required) {
        Double number = number(key, label, required);
        if (number == null) {
            return null;
        }
        if (number != Math.rint(number)) {
            throw ApiException.badRequest(label + " must be a whole number.");
        }
        return number.longValue();
    }

    Double number(String key, String label, boolean required) {
        Object value = body.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        String text = required ? required(key, label) : text(key);
        if (text == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(text);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(label + " must be a number.");
        }
    }

    /** A calendar date, normalised to YYYY-MM-DD. */
    String date(String key, String label, boolean required) {
        String text = required ? required(key, label) : text(key);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(label + " must be a valid date (YYYY-MM-DD).");
        }
    }

    /** A date and time, normalised to YYYY-MM-DDTHH:MM. */
    String dateTime(String key, String label, boolean required) {
        String text = required ? required(key, label) : text(key);
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text).format(DATE_TIME);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(label + " must be a valid date and time.");
        }
    }

    /** One of a fixed set of values; falls back to the default when the field is absent. */
    String choice(String key, String label, String fallback, String... allowed) {
        String value = text(key);
        if (value == null) {
            return fallback;
        }
        if (!Arrays.asList(allowed).contains(value)) {
            throw ApiException.badRequest(label + " must be one of: " + String.join(", ", allowed) + ".");
        }
        return value;
    }
}
