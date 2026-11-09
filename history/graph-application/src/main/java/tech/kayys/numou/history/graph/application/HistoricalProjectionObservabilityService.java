package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore.OperationalSnapshot;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Authorized, read-only collection and publication of projection health metrics. */
public final class HistoricalProjectionObservabilityService {
    private final HistoricalProjectionOperationalStore store;
    private final HistoricalRelationshipTenantContext tenantContext;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final HistoricalProjectionMetricsPort metrics;
    private final Clock clock;

    public HistoricalProjectionObservabilityService(HistoricalProjectionOperationalStore store,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            HistoricalProjectionMetricsPort metrics, Clock clock) {
        this.store=Objects.requireNonNull(store,"store");
        this.tenantContext=Objects.requireNonNull(tenantContext,"tenantContext");
        this.authorization=Objects.requireNonNull(authorization,"authorization");
        this.metrics=Objects.requireNonNull(metrics,"metrics");
        this.clock=Objects.requireNonNull(clock,"clock");
    }

    public OperationalSnapshot collect(UUID tenantId) {
        authorize(tenantId);
        OperationalSnapshot snapshot=store.snapshot(tenantId,clock.instant());
        metrics.record(snapshot);
        return snapshot;
    }

    private void authorize(UUID tenantId) {
        if(tenantId==null) {
            authorization.requireCanAuditPublicCorpus();
            return;
        }
        UUID trusted=Objects.requireNonNull(tenantContext.requireTenantId(),"trusted tenant context must provide a tenant");
        if(!trusted.equals(tenantId)) throw new SecurityException("requested metrics scope differs from authenticated tenant");
        authorization.requireCanAuditTenant(trusted);
    }
}
