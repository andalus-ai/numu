package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Generation;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Status;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authorized lifecycle for staged graph generations. */
public final class HistoricalProjectionGenerationService {
    private final HistoricalProjectionGenerationStore store;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final HistoricalProjectionGenerationValidationPort validation;
    private final Clock clock;

    public HistoricalProjectionGenerationService(HistoricalProjectionGenerationStore store,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            HistoricalProjectionGenerationValidationPort validation, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.validation = Objects.requireNonNull(validation, "validation");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Generation create(UUID tenantId) {
        return create(tenantId, UUID.randomUUID());
    }

    /** Idempotent creation used by the durable generation orchestrator. */
    public Generation create(UUID tenantId, UUID generationId) {
        authorize(tenantId);
        Objects.requireNonNull(generationId, "generationId");
        Optional<Generation> existing = store.find(tenantId, generationId);
        if (existing.isPresent()) return existing.get();
        try {
            return store.create(tenantId, generationId, clock.instant());
        } catch (RuntimeException race) {
            return store.find(tenantId, generationId).orElseThrow(() -> race);
        }
    }

    public Optional<Generation> find(UUID tenantId, UUID generationId) {
        authorize(tenantId);
        return store.find(tenantId, generationId);
    }

    public Generation markValidatedReady(UUID tenantId, UUID generationId) {
        authorize(tenantId);
        Generation current = store.find(tenantId, generationId)
                .orElseThrow(() -> new IllegalArgumentException("generation does not exist"));
        if (current.status() != Status.BUILDING) {
            if (current.status() == Status.READY || current.status() == Status.ACTIVE || current.status() == Status.RETAINED) {
                return current;
            }
            throw new IllegalStateException("generation cannot become READY from " + current.status());
        }
        var result = validation.validate(tenantId, generationId);
        if (!result.passed()) {
            throw new IllegalStateException("generation validation failed: " + result.detail());
        }
        if (!store.markReady(tenantId, generationId, clock.instant())) {
            Generation latest = store.find(tenantId, generationId).orElseThrow();
            if (latest.status() != Status.READY && latest.status() != Status.ACTIVE && latest.status() != Status.RETAINED) {
                throw new IllegalStateException("generation is missing or is not BUILDING");
            }
            return latest;
        }
        return store.find(tenantId, generationId).orElseThrow();
    }

    /** Compare-and-set activation; a retry after successful activation is harmless. */
    public Generation activate(UUID tenantId, UUID generationId, UUID expectedActiveGenerationId) {
        authorize(tenantId);
        Optional<Generation> active = store.active(tenantId);
        if (active.isPresent() && active.get().generationId().equals(generationId)) return active.get();
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
