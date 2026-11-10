package tech.kayys.numou.feedback.domain;

import java.util.Objects;
import java.util.UUID;

public record FeedbackId(UUID value) {
    public FeedbackId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static FeedbackId generate() {
        return new FeedbackId(UUID.randomUUID());
    }

    public static FeedbackId of(UUID value) {
        return new FeedbackId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}