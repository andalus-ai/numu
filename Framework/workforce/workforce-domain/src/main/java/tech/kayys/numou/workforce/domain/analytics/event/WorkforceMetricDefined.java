package tech.kayys.numou.workforce.domain.analytics.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.MetricType;
import tech.kayys.numou.workforce.domain.analytics.WorkforceMetricDefinitionId;

import java.time.Instant;
import java.util.UUID;

public record WorkforceMetricDefined(
        UUID eventId,
        Instant occurredAt,
        WorkforceMetricDefinitionId id,
        TenantId tenantId,
        String code,
        MetricType metricType
) implements DomainEvent {
    public WorkforceMetricDefined(WorkforceMetricDefinitionId id, TenantId tenantId, String code, MetricType metricType) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code, metricType);
    }
    @Override public String eventType() { return "workforce.analytics.metric.defined"; }
}
