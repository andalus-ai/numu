package tech.kayys.numou.event.application.replay;

import java.util.Objects;
import java.util.UUID;

/** Result of a replay request without exposing the raw event payload. */
public record DeadLetterReplayResult(Status status, UUID eventId) {
    public DeadLetterReplayResult {
        Objects.requireNonNull(status, "status must not be null");
    }

    public enum Status {
        PUBLISHED,
        ALREADY_CLAIMED
    }
}
