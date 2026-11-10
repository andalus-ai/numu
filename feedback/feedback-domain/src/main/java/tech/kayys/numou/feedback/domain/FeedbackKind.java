package tech.kayys.numou.feedback.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Extensible validated code; constants are conveniences, not a closed set. */
public record FeedbackKind(String code) {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9._-]{1,63}");
    public static final FeedbackKind COMMENT = new FeedbackKind("comment");
    public static final FeedbackKind BUG = new FeedbackKind("bug");
    public static final FeedbackKind IDEA = new FeedbackKind("idea");
    public static final FeedbackKind ERROR_REPORT = new FeedbackKind("error-report");
    public static final FeedbackKind COMPLAINT = new FeedbackKind("complaint");
    public static final FeedbackKind REVIEW = new FeedbackKind("review");
    public static final FeedbackKind OTHER = new FeedbackKind("other");

    public FeedbackKind {
        Objects.requireNonNull(code, "code cannot be null");
        code = code.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Feedback kind must be 2-64 lowercase ASCII characters and start with a letter");
        }
    }
    public static FeedbackKind of(String code) { return new FeedbackKind(code); }
}