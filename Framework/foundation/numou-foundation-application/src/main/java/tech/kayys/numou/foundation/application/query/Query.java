package tech.kayys.numou.foundation.application.query;

/**
 * Marker interface for a read-intent message: "give me a view of the
 * system." Queries never change state and are free to be served by a
 * dedicated read model / projection instead of the write-side
 * aggregates (see CQRS read-model guidance in the platform README).
 */
public interface Query {
}
