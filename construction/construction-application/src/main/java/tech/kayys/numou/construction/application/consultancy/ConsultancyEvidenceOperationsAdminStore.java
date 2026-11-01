package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for operator inspection and explicit terminal recovery. */
public interface ConsultancyEvidenceOperationsAdminStore {
    Uni<List<ConsultancyEvidenceOperation>> list(
            String tenantId, Optional<ConsultancyEvidenceOperation.Status> status, int limit, int offset);

    /** Atomically requeues a terminal operation and appends its immutable audit record. */
    Uni<ConsultancyEvidenceOperation> recoverTerminalFailure(RecoveryRequest request);

    record RecoveryRequest(String tenantId, UUID operationId, UUID actorId,
            String reason, String idempotencyKey, Instant occurredAt) {
        public RecoveryRequest {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (operationId == null || actorId == null || occurredAt == null) {
                throw new IllegalArgumentException("operationId, actorId and occurredAt are required");
            }
            tenantId = tenantId.trim();
            reason = requireText(reason, "reason");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            if (reason.length() > 2000) throw new IllegalArgumentException("reason exceeds 2000 characters");
            if (idempotencyKey.length() > 200) throw new IllegalArgumentException("idempotencyKey exceeds 200 characters");
        }
        private static String requireText(String value, String label) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
            return value.trim();
        }
    }
}
