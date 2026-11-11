package tech.kayys.numou.history.graph.application;

import java.util.UUID;

/** Fail-closed validation contract required before a staging generation can become READY. */
@FunctionalInterface
public interface HistoricalProjectionGenerationValidationPort {
    ValidationResult validate(UUID tenantId, UUID generationId);

    record ValidationResult(boolean complete, long canonicalRelationships, long projectedRelationships,
                            long missing, long stale, long divergent, long orphaned, String detail) {
        public ValidationResult {
            if (canonicalRelationships < 0 || projectedRelationships < 0 || missing < 0
                    || stale < 0 || divergent < 0 || orphaned < 0) {
                throw new IllegalArgumentException("validation counts cannot be negative");
            }
        }

        public boolean passed() {
            return complete && canonicalRelationships == projectedRelationships
                    && missing == 0 && stale == 0 && divergent == 0 && orphaned == 0;
        }
    }
}
