package tech.kayys.numou.omnichannel.core;

import java.util.Objects;

/** Outcome for one item in an operator-requested replay batch. */
public record DeadLetterReplayBatchItem(InboundEventKey key, Outcome outcome) {
    public enum Outcome {
        REQUEUED,
        NOT_FOUND_OR_NOT_DEAD_LETTER,
        FORBIDDEN
    }

    public DeadLetterReplayBatchItem {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }
}
