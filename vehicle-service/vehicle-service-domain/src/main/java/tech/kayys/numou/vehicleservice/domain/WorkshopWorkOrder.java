package tech.kayys.numou.vehicleservice.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer-facing workshop work order. External capability identifiers are stored as references;
 * reservation, vehicle registry, customer identity, pricing and payment remain owned elsewhere.
 */
public final class WorkshopWorkOrder {
    private final WorkshopWorkOrderId id;
    private final String tenantId;
    private final UUID customerId;
    private final UUID vehicleId;
    private final ServiceOfferingId serviceOfferingId;
    private final UUID reservationId;
    private final String customerConcern;
    private final Instant createdAt;
    private WorkshopWorkOrderStatus status;
    private UUID assignedTechnicianId;
    private Instant startedAt;
    private Instant completedAt;
    private String cancellationReason;

    private WorkshopWorkOrder(WorkshopWorkOrderId id, String tenantId, UUID customerId, UUID vehicleId,
                              ServiceOfferingId serviceOfferingId, UUID reservationId,
                              String customerConcern, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = requiredText(tenantId, "tenantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        this.serviceOfferingId = Objects.requireNonNull(serviceOfferingId, "serviceOfferingId");
        this.reservationId = reservationId;
        this.customerConcern = requiredText(customerConcern, "customerConcern");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = WorkshopWorkOrderStatus.DRAFT;
    }

    public static WorkshopWorkOrder create(WorkshopWorkOrderId id, String tenantId, UUID customerId,
                                           UUID vehicleId, ServiceOfferingId serviceOfferingId,
                                           UUID reservationId, String customerConcern, Instant createdAt) {
        return new WorkshopWorkOrder(id, tenantId, customerId, vehicleId, serviceOfferingId,
                reservationId, customerConcern, createdAt);
    }

    public void requestCustomerApproval() {
        transition(WorkshopWorkOrderStatus.DRAFT, WorkshopWorkOrderStatus.AWAITING_CUSTOMER_APPROVAL);
    }

    public void approveByCustomer() {
        transition(WorkshopWorkOrderStatus.AWAITING_CUSTOMER_APPROVAL, WorkshopWorkOrderStatus.APPROVED);
    }

    public void schedule() {
        if (status != WorkshopWorkOrderStatus.APPROVED) throw invalidTransition("schedule");
        status = WorkshopWorkOrderStatus.SCHEDULED;
    }

    public void assignTechnician(UUID technicianId) {
        if (status == WorkshopWorkOrderStatus.IN_PROGRESS
                || status == WorkshopWorkOrderStatus.READY_FOR_PICKUP || isTerminal()) {
            throw invalidTransition("assign technician");
        }
        assignedTechnicianId = Objects.requireNonNull(technicianId, "technicianId");
    }

    public void start(Instant now) {
        if (status != WorkshopWorkOrderStatus.APPROVED && status != WorkshopWorkOrderStatus.SCHEDULED) {
            throw invalidTransition("start");
        }
        if (assignedTechnicianId == null) {
            throw new IllegalStateException("A technician must be assigned before work starts");
        }
        Instant started = Objects.requireNonNull(now, "now");
        if (started.isBefore(createdAt)) {
            throw new IllegalArgumentException("now cannot be before work-order creation time");
        }
        startedAt = started;
        status = WorkshopWorkOrderStatus.IN_PROGRESS;
    }

    public void hold() {
        transition(WorkshopWorkOrderStatus.IN_PROGRESS, WorkshopWorkOrderStatus.ON_HOLD);
    }

    public void resume() {
        transition(WorkshopWorkOrderStatus.ON_HOLD, WorkshopWorkOrderStatus.IN_PROGRESS);
    }

    public void markReadyForPickup() {
        transition(WorkshopWorkOrderStatus.IN_PROGRESS, WorkshopWorkOrderStatus.READY_FOR_PICKUP);
    }

    public void complete(Instant now) {
        if (status != WorkshopWorkOrderStatus.READY_FOR_PICKUP) throw invalidTransition("complete");
        Instant completed = Objects.requireNonNull(now, "now");
        if (startedAt == null || completed.isBefore(startedAt)) {
            throw new IllegalArgumentException("completion time cannot be before start time");
        }
        completedAt = completed;
        status = WorkshopWorkOrderStatus.COMPLETED;
    }

    public void cancel(String reason) {
        if (isTerminal()) throw invalidTransition("cancel");
        cancellationReason = requiredText(reason, "reason");
        status = WorkshopWorkOrderStatus.CANCELLED;
    }

    private void transition(WorkshopWorkOrderStatus expected, WorkshopWorkOrderStatus next) {
        if (status != expected) throw invalidTransition("transition to " + next);
        status = next;
    }

    private IllegalStateException invalidTransition(String action) {
        return new IllegalStateException("Cannot " + action + " work order in state " + status);
    }

    private boolean isTerminal() {
        return status == WorkshopWorkOrderStatus.COMPLETED || status == WorkshopWorkOrderStatus.CANCELLED;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public WorkshopWorkOrderId id() { return id; }
    public String tenantId() { return tenantId; }
    public UUID customerId() { return customerId; }
    public UUID vehicleId() { return vehicleId; }
    public ServiceOfferingId serviceOfferingId() { return serviceOfferingId; }
    public UUID reservationId() { return reservationId; }
    public String customerConcern() { return customerConcern; }
    public Instant createdAt() { return createdAt; }
    public WorkshopWorkOrderStatus status() { return status; }
    public UUID assignedTechnicianId() { return assignedTechnicianId; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public String cancellationReason() { return cancellationReason; }
}
