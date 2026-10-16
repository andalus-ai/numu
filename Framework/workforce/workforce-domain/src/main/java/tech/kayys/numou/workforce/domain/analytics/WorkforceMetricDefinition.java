package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.event.WorkforceMetricDefined;

import java.time.Instant;
import java.util.Objects;

public final class WorkforceMetricDefinition extends AbstractAggregateRoot<WorkforceMetricDefinitionId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private String description;
    private final MetricType metricType;
    private String unit;
    private MetricStatus status;

    private WorkforceMetricDefinition(
            WorkforceMetricDefinitionId id,
            TenantId tenantId,
            String code,
            String name,
            String description,
            MetricType metricType,
            String unit
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.metricType = Objects.requireNonNull(metricType, "metricType must not be null");
        this.unit = unit;
        this.status = MetricStatus.ACTIVE;
    }

    public static WorkforceMetricDefinition define(
            WorkforceMetricDefinitionId id,
            TenantId tenantId,
            String code,
            String name,
            String description,
            MetricType metricType,
            String unit
    ) {
        WorkforceMetricDefinition def = new WorkforceMetricDefinition(id, tenantId, code, name, description, metricType, unit);
        def.raise(new WorkforceMetricDefined(id, tenantId, code, metricType));
        return def;
    }

    public void deactivate() {
        this.status = MetricStatus.INACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status = MetricStatus.ACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public MetricType getMetricType() { return metricType; }
    public String getUnit() { return unit; }
    public MetricStatus getStatus() { return status; }
}
