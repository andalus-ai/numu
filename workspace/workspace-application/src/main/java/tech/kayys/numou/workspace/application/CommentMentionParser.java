package tech.kayys.numou.workspace.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses explicit @handle mentions without interpreting email addresses or code identifiers. */
public final class CommentMentionParser {
    public static final int MAX_MENTIONS_PER_COMMENT = 20;
    private static final Pattern HANDLE = Pattern.compile("(?<![\\p{L}\\p{N}._@-])@([A-Za-z0-9](?:[A-Za-z0-9._-]{0,62}[A-Za-z0-9])?)");

    private CommentMentionParser() {}

    public static List<String> parse(String body) {
        if (body == null || body.isBlank()) return List.of();
        Matcher matcher = HANDLE.matcher(body);
        LinkedHashMap<String, String> handles = new LinkedHashMap<>();
        while (matcher.find()) {
            String handle = matcher.group(1);
            handles.putIfAbsent(handle.toLowerCase(Locale.ROOT), handle);
            if (handles.size() > MAX_MENTIONS_PER_COMMENT)
                throw new IllegalArgumentException("comment exceeds " + MAX_MENTIONS_PER_COMMENT + " unique mentions");
        }
        return List.copyOf(new ArrayList<>(handles.values()));
    }
}
