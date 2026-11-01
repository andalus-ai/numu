package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authorized application use cases for evidence-operation operations. */
@ApplicationScoped
public class ConsultancyEvidenceOperationsAdminService {
    private final ConsultancyEvidenceOperationsAdminStore store;
    private final ConsultancyEvidenceOperationsAdminAuthorization authorization;
    private final Clock clock;

    @Inject
    public ConsultancyEvidenceOperationsAdminService(
            ConsultancyEvidenceOperationsAdminStore store,
            ConsultancyEvidenceOperationsAdminAuthorization authorization) {
        this(store, authorization, Clock.systemUTC());
    }

    ConsultancyEvidenceOperationsAdminService(
            ConsultancyEvidenceOperationsAdminStore store,
            ConsultancyEvidenceOperationsAdminAuthorization authorization, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.authorization = Objects.requireNonNull(authorization);
        this.clock = Objects.requireNonNull(clock);
    }

    public Uni<List<ConsultancyEvidenceOperation>> list(
            String tenantId, UUID actorId, Optional<ConsultancyEvidenceOperation.Status> status,
            int limit, int offset) {
        String tenant = requireTenant(tenantId);
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(status, "status");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        return authorization.requireOperationsAccess(tenant, actorId,
                        ConsultancyEvidenceOperationsAdminAuthorization.Action.VIEW)
                .chain(() -> store.list(tenant, status, limit, offset));
    }

    public Uni<ConsultancyEvidenceOperation> recoverTerminalFailure(
            String tenantId, UUID operationId, UUID actorId, String reason, String idempotencyKey) {
        String tenant = requireTenant(tenantId);
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(actorId, "actorId");
        var request = new ConsultancyEvidenceOperationsAdminStore.RecoveryRequest(
                tenant, operationId, actorId, reason, idempotencyKey, clock.instant());
        return authorization.requireOperationsAccess(tenant, actorId,
                        ConsultancyEvidenceOperationsAdminAuthorization.Action.RECOVER_TERMINAL_FAILURE)
                .chain(() -> store.recoverTerminalFailure(request));
    }

    private static String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        return tenantId.trim();
    }
}
