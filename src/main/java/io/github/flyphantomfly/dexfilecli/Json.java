package io.github.flyphantomfly.dexfilecli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON support: a generic encoder for Map/List/String/Number/Boolean/null
 * and a strict recursive-descent parser. No third-party dependency — the fat jar
 * stays lean (see the integration plan, Phase 1).
 */
public final class Json {

    private Json() {
    }

    // ── encoding ──────────────────────────────────────────────────────────

    public static String encode(Object value) {
        StringBuilder sb = new StringBuilder();
        encode(value, sb);
        return sb.toString();
    }

    private static void encode(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            encodeString(s, sb);
        } else if (value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        } else if (value instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                sb.append("null");
            } else {
                sb.append(d);
            }
        } else if (value instanceof Float f) {
            encode(f.doubleValue(), sb);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                encodeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                encode(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                encode(item, sb);
            }
            sb.append(']');
        } else {
            encodeString(String.valueOf(value), sb);
        }
    }

    private static void encodeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Convenience: ordered object builder. */
    public static Map<String, Object> obj(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // ── parsing ───────────────────────────────────────────────────────────

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw p.fail("trailing content after JSON value");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        RuntimeException fail(String message) {
            return new IllegalArgumentException("JSON error at offset " + pos + ": " + message);
        }

        void skipWs() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) {
                throw fail("unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObj();
                case '[' -> parseArr();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, pos)) {
                throw fail("invalid literal");
            }
            pos += literal.length();
            return value;
        }

        private Map<String, Object> parseObj() {
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw fail("expected object key");
                }
                String key = parseString();
                skipWs();
                if (peek() != ':') {
                    throw fail("expected ':'");
                }
                pos++;
                map.put(key, parseValue());
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw fail("expected ',' or '}'");
                }
            }
        }

        private List<Object> parseArr() {
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw fail("expected ',' or ']'");
                }
            }
        }

        private String parseString() {
            if (peek() != '"') {
                throw fail("expected string");
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw fail("unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw fail("bad unicode escape");
                            }
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw fail("bad escape '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object parseNumber() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            if (pos == start) {
                throw fail("unexpected character '" + peek() + "'");
            }
            String num = text.substring(start, pos);
            if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException ignored) {
                    // fall through to Double
                }
            }
            return Double.parseDouble(num);
        }

        private char peek() {
            if (atEnd()) {
                throw fail("unexpected end of input");
            }
            return text.charAt(pos);
        }
    }
}
