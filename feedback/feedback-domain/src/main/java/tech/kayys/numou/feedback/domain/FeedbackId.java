package tech.kayys.numou.feedback.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record FeedbackId(UUID value) implements DomainId<UUID> {
    public FeedbackId { Objects.requireNonNull(value, "value cannot be null"); }
    public static FeedbackId generate() { return new FeedbackId(UUID.randomUUID()); }
    public static FeedbackId of(UUID value) { return new FeedbackId(value); }
    public static FeedbackId fromString(String value) { return new FeedbackId(UUID.fromString(value)); }
    @Override public String toString() { return value.toString(); }
}