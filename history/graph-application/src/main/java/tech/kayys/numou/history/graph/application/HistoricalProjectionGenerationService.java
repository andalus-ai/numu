package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Generation;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authorized lifecycle for staged graph generations. Callers may mark a generation
 * READY only after replay and reconciliation have completed successfully.
 */
public final class HistoricalProjectionGenerationService {
    private final HistoricalProjectionGenerationStore store;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final Clock clock;

    public HistoricalProjectionGenerationService(HistoricalProjectionGenerationStore store,
            HistoricalProjectionMaintenanceAuthorizationPort authorization, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Generation create(UUID tenantId) {
        authorize(tenantId);
        return store.create(tenantId, UUID.randomUUID(), clock.instant());
    }

    public Generation markValidatedReady(UUID tenantId, UUID generationId) {
        authorize(tenantId);
        if (!store.markReady(tenantId, generationId, clock.instant())) {
            throw new IllegalStateException("generation is missing or is not BUILDING");
        }
        return store.find(tenantId, generationId).orElseThrow();
    }

    /**
     * Atomically changes the active pointer if it still equals expectedActiveGenerationId.
     * Pass null only when the scope has no active generation yet.
     */
    public Generation activate(UUID tenantId, UUID generationId, UUID expectedActiveGenerationId) {
        authorize(tenantId);
        if (!store.activate(tenantId, generationId, expectedActiveGenerationId, clock.instant())) {
            throw new IllegalStateException("generation activation compare-and-set failed");
        }
        return store.find(tenantId, generationId).orElseThrow();
    }

    /** Rollback is an activation of a retained generation, never a destructive restore. */
    public Generation rollback(UUID tenantId, UUID retainedGenerationId, UUID expectedActiveGenerationId) {
        return activate(tenantId, retainedGenerationId, expectedActiveGenerationId);
    }

    public Optional<Generation> active(UUID tenantId) {
        authorize(tenantId);
        return store.active(tenantId);
    }

    private void authorize(UUID tenantId) {
        if (tenantId == null) authorization.requireCanRebuildPublicCorpus();
        else authorization.requireCanRebuildTenant(tenantId);
    }
}
