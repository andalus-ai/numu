package tech.kayys.numou.foundation.application.identifier;

import io.smallrye.mutiny.Uni;

/**
 * Outbound port for atomic and durable sequence allocation across instances (config03.md §P4-15 #6).
 */
public interface IdentifierSequenceStore {

    /**
     * Atomically increments and returns the next sequence number for the given key.
     */
    Uni<Long> allocateNext(SequenceKey key);

    record SequenceKey(
            String namespace,
            String scopeKey,
            String periodKey
    ) {
        public SequenceKey {
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalArgumentException("namespace must not be blank");
            }
            if (scopeKey == null || scopeKey.isBlank()) {
                throw new IllegalArgumentException("scopeKey must not be blank");
            }
            if (periodKey == null || periodKey.isBlank()) {
                throw new IllegalArgumentException("periodKey must not be blank");
            }
        }
    }
}
