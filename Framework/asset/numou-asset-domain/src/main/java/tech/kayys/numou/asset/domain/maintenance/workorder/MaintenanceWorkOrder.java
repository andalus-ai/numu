package tech.kayys.numou.asset.domain.maintenance.workorder;

import tech.kayys.numou.asset.domain.event.MaintenanceWorkOrderCancelled;
import tech.kayys.numou.asset.domain.event.MaintenanceWorkOrderCompleted;
import tech.kayys.numou.asset.domain.event.MaintenanceWorkOrderCreated;
import tech.kayys.numou.asset.domain.event.MaintenanceWorkOrderHeld;
import tech.kayys.numou.asset.domain.event.MaintenanceWorkOrderOpened;
import tech.kayys.numou.asset.domain.event.MaintenanceWorkOrderResumed;
import tech.kayys.numou.asset.domain.event.MaintenanceWorkOrderStarted;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Maintenance work order aggregate root (ASSET-19). References Asset by UUID identity only. */
public final class MaintenanceWorkOrder extends AbstractAggregateRoot<MaintenanceWorkOrderId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private UUID assetId;
    private String workOrderNumber;
    private String title;
    private String description;
    private MaintenanceWorkOrderType type;
    private MaintenancePriority priority;
    private MaintenanceWorkOrderStatus status;
    private String requestedBy;
    private String assignedTo;
    private Instant openedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    private MaintenanceWorkOrder() { super(); }

    private MaintenanceWorkOrder(MaintenanceWorkOrderId id, String tenantId, UUID assetId,
                                 String workOrderNumber, String title, String description,
                                 MaintenanceWorkOrderType type, MaintenancePriority priority,
                                 String requestedBy) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.workOrderNumber = requireText(workOrderNumber, "workOrderNumber");
        this.title = requireText(title, "title");
        this.description = description;
        this.type = Objects.requireNonNull(type, "type");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.requestedBy = requestedBy;
        this.status = MaintenanceWorkOrderStatus.DRAFT;
    }

    public static MaintenanceWorkOrder create(MaintenanceWorkOrderId id, String tenantId, UUID assetId,
                                              String workOrderNumber, String title, String description,
                                              MaintenanceWorkOrderType type, MaintenancePriority priority,
                                              String requestedBy, DomainClock clock) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clock, "clock");
        MaintenanceWorkOrder wo = new MaintenanceWorkOrder(id, tenantId, assetId, workOrderNumber,
                title, description, type, priority, requestedBy);
        Instant now = clock.now();
        wo.setCreatedAt(now);
        wo.setUpdatedAt(now);
        wo.raise(new MaintenanceWorkOrderCreated(UUID.randomUUID(), now, id.value(), assetId, workOrderNumber));
        return wo;
    }

    public static MaintenanceWorkOrder reconstitute(MaintenanceWorkOrderId id, String tenantId, UUID assetId,
                                                    String workOrderNumber, String title, String description,
                                                    MaintenanceWorkOrderType type, MaintenancePriority priority,
                                                    MaintenanceWorkOrderStatus status, String requestedBy,
                                                    String assignedTo, Instant openedAt, Instant startedAt,
                                                    Instant completedAt, Instant cancelledAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        MaintenanceWorkOrder wo = new MaintenanceWorkOrder(id, tenantId, assetId, workOrderNumber,
                title, description, Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(priority, "priority"), requestedBy);
        wo.status = status;
        wo.assignedTo = assignedTo;
        wo.openedAt = openedAt;
        wo.startedAt = startedAt;
        wo.completedAt = completedAt;
        wo.cancelledAt = cancelledAt;
        return wo;
    }
    public void assign(String assignedTo, DomainClock clock) {
        this.assignedTo = assignedTo;
        touch(clock);
    }

    public void open(DomainClock clock) {
        requireStatus(MaintenanceWorkOrderStatus.DRAFT);
        this.status = MaintenanceWorkOrderStatus.OPEN;
        this.openedAt = clock.now();
        touch(clock);
        raise(new MaintenanceWorkOrderOpened(UUID.randomUUID(), this.openedAt, id().value(), assetId));
    }

    public void start(DomainClock clock) {
        requireStatus(MaintenanceWorkOrderStatus.OPEN);
        this.status = MaintenanceWorkOrderStatus.IN_PROGRESS;
        this.startedAt = clock.now();
        touch(clock);
        raise(new MaintenanceWorkOrderStarted(UUID.randomUUID(), this.startedAt, id().value(), assetId));
    }

    public void hold(DomainClock clock) {
        requireStatus(MaintenanceWorkOrderStatus.IN_PROGRESS);
        this.status = MaintenanceWorkOrderStatus.ON_HOLD;
        touch(clock);
        raise(new MaintenanceWorkOrderHeld(UUID.randomUUID(), getUpdatedAt(), id().value(), assetId));
    }

    public void resume(DomainClock clock) {
        requireStatus(MaintenanceWorkOrderStatus.ON_HOLD);
        this.status = MaintenanceWorkOrderStatus.IN_PROGRESS;
        touch(clock);
        raise(new MaintenanceWorkOrderResumed(UUID.randomUUID(), getUpdatedAt(), id().value(), assetId));
    }

    public void complete(DomainClock clock) {
        requireStatus(MaintenanceWorkOrderStatus.IN_PROGRESS);
        this.status = MaintenanceWorkOrderStatus.COMPLETED;
        this.completedAt = clock.now();
        touch(clock);
        raise(new MaintenanceWorkOrderCompleted(UUID.randomUUID(), this.completedAt, id().value(), assetId));
    }

    public void cancel(DomainClock clock) {
        requireStatus(MaintenanceWorkOrderStatus.DRAFT, MaintenanceWorkOrderStatus.OPEN);
        this.status = MaintenanceWorkOrderStatus.CANCELLED;
        this.cancelledAt = clock.now();
        touch(clock);
        raise(new MaintenanceWorkOrderCancelled(UUID.randomUUID(), this.cancelledAt, id().value(), assetId));
    }

    private void requireStatus(MaintenanceWorkOrderStatus... allowed) {
        for (MaintenanceWorkOrderStatus candidate : allowed) {
            if (status == candidate) return;
        }
        throw new InvalidStateException("Work order cannot transition from " + status);
    }

    private void touch(DomainClock clock) {
        Objects.requireNonNull(clock, "clock");
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new BusinessRuleViolation(field + " cannot be blank");
        return normalized;
    }

    public String tenantId() { return tenantId; }
    public UUID assetId() { return assetId; }
    public String workOrderNumber() { return workOrderNumber; }
    public String title() { return title; }
    public String description() { return description; }
    public MaintenanceWorkOrderType type() { return type; }
    public MaintenancePriority priority() { return priority; }
    public MaintenanceWorkOrderStatus status() { return status; }
    public String requestedBy() { return requestedBy; }
    public String assignedTo() { return assignedTo; }
    public Instant openedAt() { return openedAt; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant cancelledAt() { return cancelledAt; }
}

