package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.Action;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.RebuildRun;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Authorized operator commands for pausing, resuming, cancelling, retrying and
 * marking a projection rebuild failed. Every successful transition is persisted
 * together with its actor-attributed audit entry by the store.
 */
public final class HistoricalProjectionRebuildLifecycleService {
    private final HistoricalProjectionRebuildStore store;
    private final HistoricalRelationshipTenantContext tenantContext;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final HistoricalProjectionOperatorContext operatorContext;
    private final Clock clock;

    public HistoricalProjectionRebuildLifecycleService(HistoricalProjectionRebuildStore store,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            HistoricalProjectionOperatorContext operatorContext, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.operatorContext = Objects.requireNonNull(operatorContext, "operatorContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RebuildRun inspect(UUID tenantId, UUID runId) {
        authorize(tenantId, false);
        return find(tenantId, runId);
    }

    public RebuildRun pause(UUID tenantId, UUID runId, String reason) {
        return transition(tenantId, runId, Action.PAUSE, RebuildRun.Status.RUNNING,
                RebuildRun.Status.PAUSED, reason);
    }

    public RebuildRun resume(UUID tenantId, UUID runId, String reason) {
        return transition(tenantId, runId, Action.RESUME, RebuildRun.Status.PAUSED,
                RebuildRun.Status.RUNNING, reason);
    }

    public RebuildRun cancel(UUID tenantId, UUID runId, String reason) {
        authorize(tenantId, true);
        RebuildRun run = find(tenantId, runId);
        if (run.status() != RebuildRun.Status.RUNNING && run.status() != RebuildRun.Status.PAUSED
                && run.status() != RebuildRun.Status.FAILED) {
            throw new IllegalStateException("only running, paused, or failed rebuilds can be cancelled");
        }
        return apply(tenantId, run, Action.CANCEL, RebuildRun.Status.CANCELLED, reason);
    }

    public RebuildRun retry(UUID tenantId, UUID runId, String reason) {
        return transition(tenantId, runId, Action.RETRY, RebuildRun.Status.FAILED,
                RebuildRun.Status.RUNNING, reason);
    }

    /** Worker/runtime failure must carry a configured service-principal actor. */
    public RebuildRun markFailed(UUID tenantId, UUID runId, String reason) {
        return transition(tenantId, runId, Action.FAIL, RebuildRun.Status.RUNNING,
                RebuildRun.Status.FAILED, reason);
    }

    private RebuildRun transition(UUID tenantId, UUID runId, Action action,
                                  RebuildRun.Status expected, RebuildRun.Status target, String reason) {
        authorize(tenantId, true);
        RebuildRun run = find(tenantId, runId);
        if (run.status() != expected) {
            throw new IllegalStateException("rebuild state changed; expected " + expected + " but was " + run.status());
        }
        return apply(tenantId, run, action, target, reason);
    }

    private RebuildRun apply(UUID tenantId, RebuildRun run, Action action,
                             RebuildRun.Status target, String reason) {
        UUID actorId = Objects.requireNonNull(operatorContext.requireActorId(),
                "authenticated operator context must provide an actor");
        boolean changed = store.transition(tenantId, run.runId(), actorId, action,
                run.lifecycleVersion(), run.status(), target, safe(reason), clock.instant());
        if (!changed) {
            throw new IllegalStateException("rebuild lifecycle changed concurrently; reload before retrying");
        }
        return find(tenantId, run.runId());
    }

    private RebuildRun find(UUID tenantId, UUID runId) {
        Objects.requireNonNull(runId, "runId");
        return store.find(tenantId, runId)
                .orElseThrow(() -> new IllegalArgumentException("rebuild run does not exist in this scope"));
    }

    private void authorize(UUID tenantId, boolean mutate) {
        if (tenantId == null) {
            if (mutate) authorization.requireCanRebuildPublicCorpus();
            authorization.requireCanAuditPublicCorpus();
            return;
        }
        UUID trustedTenant = Objects.requireNonNull(tenantContext.requireTenantId(),
                "trusted tenant context must provide a tenant");
        if (!trustedTenant.equals(tenantId)) {
            throw new SecurityException("requested rebuild scope differs from authenticated tenant");
        }
        if (mutate) authorization.requireCanRebuildTenant(trustedTenant);
        authorization.requireCanAuditTenant(trustedTenant);
    }

    private static String safe(String detail) {
        String value = Objects.toString(detail, "").replaceAll("[\\r\\n\\t]+", " ");
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
