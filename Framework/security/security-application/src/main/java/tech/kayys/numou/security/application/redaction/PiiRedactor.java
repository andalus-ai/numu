package tech.kayys.syirkah.security.application.redaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personal and secret data before it reaches logs, traces or error bodies.
 * Reservations carry guest names, phones and emails, so this is not optional.
 *
 * <p>Deliberately conservative: it may over-mask a long digit string, which is
 * the safe direction for a log filter.
 */
public final class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    private static final Pattern CARD = Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])(\\+?\\d[\\d\\s().-]{7,}\\d)(?![\\w])");

    private PiiRedactor() { }

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = JWT.matcher(input).replaceAll("[REDACTED_TOKEN]");
        out = BEARER.matcher(out).replaceAll("$1[REDACTED_TOKEN]");
        out = EMAIL.matcher(out).replaceAll("$1***@$2");
        out = CARD.matcher(out).replaceAll(m -> luhnValid(m.group().replaceAll("\\D", ""))
                ? "[REDACTED_CARD]" : Matcher.quoteReplacement(m.group()));
        out = maskPhones(out);
        return out;
    }

    private static boolean luhnValid(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl && (d *= 2) > 9) d -= 9;
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    private static String maskPhones(String in) {
        final Matcher m = PHONE.matcher(in);
        final var sb = new StringBuilder();
        while (m.find()) {
            final String digits = m.group(1).replaceAll("\\D", "");
            final String last = digits.length() >= 2 ? digits.substring(digits.length() - 2) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement("***" + last));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Masks a name to initial + length-preserving stars, e.g. "Budi Santoso" → "B*** S*******". */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        final var parts = name.trim().split("\\s+");
        final var sb = new StringBuilder();
        for (final String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(p.charAt(0)).append("*".repeat(Math.max(0, p.length() - 1)));
        }
        return sb.toString();
    }
}
