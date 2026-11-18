package gym;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer so the project needs no libraries beyond the JDBC driver.
 * Objects map to Map, arrays to List, numbers to Long or Double.
 */
final class Json {

    private Json() {
    }

    static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, item);
            }
            out.append(']');
        } else {
            quote(out, value.toString());
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Parses JSON text; throws IllegalArgumentException when it is malformed. */
    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.skipSpace();
        if (parser.pos < text.length()) {
            throw parser.error("unexpected text after value");
        }
        return value;
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at position " + pos + ": " + message);
        }

        void skipSpace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            skipSpace();
            if (pos >= src.length()) {
                throw error("unexpected end of input");
            }
            return src.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }

        Object value() {
            char c = peek();
            if (c == '{') {
                return object();
            }
            if (c == '[') {
                return array();
            }
            if (c == '"') {
                return string();
            }
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                if (peek() != '"') {
                    throw error("expected a string key");
                }
                String key = string();
                expect(':');
                map.put(key, value());
                if (peek() == ',') {
                    pos++;
                } else {
                    expect('}');
                    return map;
                }
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            expect('[');
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                if (peek() == ',') {
                    pos++;
                } else {
                    expect(']');
                    return list;
                }
            }
        }

        String string() {
            StringBuilder out = new StringBuilder();
            pos++; // opening quote
            while (true) {
                if (pos >= src.length()) {
                    throw error("unterminated string");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= src.length()) {
                    throw error("unterminated escape");
                }
                char e = src.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (pos + 4 > src.length()) {
                            throw error("bad unicode escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape '\\" + e + "'");
                }
            }
        }

        Number number() {
            int start = pos;
            while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) {
                pos++;
            }
            String token = src.substring(start, pos);
            try {
                if (token.matches("-?\\d{1,18}")) {
                    return Long.parseLong(token);
                }
                return Double.parseDouble(token);
            } catch (NumberFormatException e) {
                pos = start;
                throw error("unexpected value");
            }
        }
    }
}
