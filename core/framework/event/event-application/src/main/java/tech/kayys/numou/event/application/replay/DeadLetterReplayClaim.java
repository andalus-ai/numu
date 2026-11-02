package tech.kayys.numou.event.application.replay;

import java.util.Objects;
import java.util.UUID;

/** Fenced, durable claim returned only to the process that won the replay race. */
public record DeadLetterReplayClaim(UUID claimToken, DeadLetterReplayRecord record) {
    public DeadLetterReplayClaim {
        Objects.requireNonNull(claimToken, "claimToken must not be null");
        Objects.requireNonNull(record, "record must not be null");
    }
}
