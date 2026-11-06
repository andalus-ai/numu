package tech.kayys.numou.history.graph.application;

/** Raised when a concurrent writer advances a relationship revision first. */
public final class HistoricalRelationshipConflictException extends RuntimeException {
    public HistoricalRelationshipConflictException(String message) {
        super(message);
    }
}
