package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceCycleClosed;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceCycleCreated;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceCycleOpened;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class PerformanceCycle extends AbstractAggregateRoot<PerformanceCycleId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private PerformanceCycleStatus status;

    private PerformanceCycle(
            PerformanceCycleId id,
            TenantId tenantId,
            String code,
            String name,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Period end cannot be before period start");
        }
        this.status = PerformanceCycleStatus.DRAFT;
    }

    public static PerformanceCycle create(
            PerformanceCycleId id,
            TenantId tenantId,
            String code,
            String name,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        PerformanceCycle cycle = new PerformanceCycle(id, tenantId, code, name, periodStart, periodEnd);
        cycle.raise(new PerformanceCycleCreated(id, tenantId, code));
        return cycle;
    }

    public void open() {
        if (status != PerformanceCycleStatus.DRAFT) {
            throw new IllegalStateException("Only draft cycles can be opened");
        }
        this.status = PerformanceCycleStatus.OPEN;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceCycleOpened(getId()));
    }

    public void startReview() {
        if (status != PerformanceCycleStatus.OPEN) {
            throw new IllegalStateException("Only open cycles can transition to reviewing");
        }
        this.status = PerformanceCycleStatus.REVIEWING;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void close() {
        if (status != PerformanceCycleStatus.REVIEWING && status != PerformanceCycleStatus.OPEN) {
            throw new IllegalStateException("Only open or reviewing cycles can be closed");
        }
        this.status = PerformanceCycleStatus.CLOSED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceCycleClosed(getId()));
    }

    public void cancel() {
        if (status == PerformanceCycleStatus.CLOSED) {
            throw new IllegalStateException("Closed cycles cannot be cancelled");
        }
        this.status = PerformanceCycleStatus.CANCELLED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == PerformanceCycleStatus.OPEN;
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public PerformanceCycleStatus getStatus() { return status; }
}
