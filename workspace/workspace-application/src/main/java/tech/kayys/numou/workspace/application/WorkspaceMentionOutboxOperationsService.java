package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxAdminPolicy;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore.DeadMention;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated application boundary for inspecting and replaying dead mention deliveries.
 * Tenant and actor identity must come from the host's trusted identity provider.
 */
public final class WorkspaceMentionOutboxOperationsService {
    public static final int MAX_DEAD_LETTER_PAGE_SIZE = 200;

    private final WorkspaceMentionOutboxStore store;
    private final WorkspaceMentionOutboxAdminPolicy authorization;
    private final Clock clock;

    public WorkspaceMentionOutboxOperationsService(
            WorkspaceMentionOutboxStore store,
            WorkspaceMentionOutboxAdminPolicy authorization,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<DeadMention> listDead(UUID tenantId, UUID actorId, int limit) {
        identity(tenantId, actorId);
        if (limit < 1 || limit > MAX_DEAD_LETTER_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be 1.."
                    + MAX_DEAD_LETTER_PAGE_SIZE);
        }
        authorization.requireManage(tenantId, actorId);
        return List.copyOf(store.listDead(tenantId, limit));
    }

    /**
     * Requeues only a dead row belonging to the supplied tenant. False deliberately
     * conflates missing, foreign-tenant, and non-dead rows to avoid an existence oracle.
     */
    public boolean requeueDead(UUID tenantId, UUID actorId, UUID deliveryId) {
        identity(tenantId, actorId);
        Objects.requireNonNull(deliveryId, "deliveryId");
        authorization.requireManage(tenantId, actorId);
        return store.requeueDead(tenantId, deliveryId, actorId, clock.instant());
    }

    private static void identity(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
    }
}
