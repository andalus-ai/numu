package tech.kayys.numou.feedback.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Opaque principal reference; identity remains owned by the host capability. */
public record FeedbackActor(String actorType, String actorId) {
    private static final Pattern TYPE = Pattern.compile("[a-z][a-z0-9._-]{1,31}");
    public FeedbackActor {
        Objects.requireNonNull(actorType, "actorType cannot be null");
        Objects.requireNonNull(actorId, "actorId cannot be null");
        actorType = actorType.trim().toLowerCase(Locale.ROOT);
        actorId = actorId.trim();
        if (!TYPE.matcher(actorType).matches()) throw new IllegalArgumentException("Invalid actorType");
        if (actorId.isEmpty() || actorId.length() > 200) throw new IllegalArgumentException("actorId must contain 1-200 characters");
    }
}