package tech.kayys.numou.mobility.ride.domain;

import java.util.regex.Pattern;

/**
 * Keeps conversations on the platform. A rider and a driver need to say "I'm at the blue gate", not to swap numbers (which would move
 * the next booking, and any dispute, off the platform and defeat the number masking). Phone-like digit runs, emails and links are replaced.
 */
public final class ChatFilter {

    private static final Pattern PHONE = Pattern.compile("(?<![\\w])\\+?\\d[\\d\\s().-]{6,}\\d(?![\\w])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");

    private ChatFilter() { }

    public static String sanitize(String raw) {
        String t = raw.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").strip();
        t = LINK.matcher(t).replaceAll("[link hidden]");
        t = EMAIL.matcher(t).replaceAll("[email hidden]");
        t = PHONE.matcher(t).replaceAll(m -> m.group().replaceAll("\\D", "").length() >= 8 ? "[number hidden]" : java.util.regex.Matcher.quoteReplacement(m.group()));
        return t;
    }
}
