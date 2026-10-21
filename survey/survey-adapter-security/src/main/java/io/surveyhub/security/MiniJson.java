package io.surveyhub.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, dependency-free JSON reader for small trusted-format documents (JWT headers and claims).
 * Produces Map / List / String / Double / Boolean / null. Depth and size are bounded.
 */
final class MiniJson {
    private static final int MAX_DEPTH = 16;
    private final String s;
    private int i;

    private MiniJson(String s) { this.s = s; }

    static Object parse(String text) {
        if (text.length() > 16 * 1024) throw new AuthenticationException("JSON too large");
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != text.length()) throw p.error("trailing characters");
        return v;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) throw error("too deep");
        if (i >= s.length()) throw error("unexpected end");
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (peek('}')) { i++; return m; }
        while (true) {
            ws();
            if (!peek('"')) throw error("expected key");
            String k = string();
            ws(); expect(':'); ws();
            if (m.put(k, value(depth + 1)) != null) throw error("duplicate key " + k); // ambiguity is an attack surface
            ws();
            if (peek(',')) { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> array(int depth) {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (peek(']')) { i++; return l; }
        while (true) {
            ws();
            l.add(value(depth + 1));
            ws();
            if (peek(',')) { i++; continue; }
            expect(']');
            return l;
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw error("unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c < 0x20) throw error("control character in string");
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw error("bad escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> b.append(e);
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) throw error("bad unicode escape");
                    try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); } catch (NumberFormatException ex) { throw error("bad unicode escape"); }
                    i += 4;
                }
                default -> throw error("bad escape");
            }
        }
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, i)) throw error("unexpected token");
        i += word.length();
        return value;
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("unexpected character");
        try { return Double.valueOf(s.substring(start, i)); } catch (NumberFormatException e) { throw error("bad number"); }
    }

    private void ws() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }

    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

    private void expect(char c) { if (!peek(c)) throw error("expected '" + c + "'"); i++; }

    private AuthenticationException error(String m) { return new AuthenticationException("Malformed JSON: " + m); }
}
