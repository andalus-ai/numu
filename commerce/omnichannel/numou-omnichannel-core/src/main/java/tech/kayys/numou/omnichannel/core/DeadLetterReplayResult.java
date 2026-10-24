package tech.kayys.numou.omnichannel.core;

/** A replay only requeues the same durable event identity; it never creates a new event. */
public enum DeadLetterReplayResult {
    REQUEUED,
    NOT_FOUND_OR_NOT_DEAD_LETTER
}
