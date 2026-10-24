package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Tenant-owned courier assignment and delivery outcome. POD stores references to evidence
 * managed by a protected media service; the aggregate never stores raw images or signatures.
 */
public final class DeliveryTask {
    private final DeliveryTaskId id;
    private final TenantId tenantId;
    private final ShipmentId shipmentId;
    private final String courierReference;
    private final String assignedBy;
    private final Instant createdAt;
    private DeliveryTaskStatus status;
    private Instant updatedAt;
    private Instant outForDeliveryAt;
    private Instant lastAttemptAt;
    private Instant deliveredAt;
    private String recipientName;
    private String proofReference;
    private String failureReason;

    private DeliveryTask(DeliveryTaskId id, TenantId tenantId, ShipmentId shipmentId,
                         String courierReference, String assignedBy, Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.shipmentId = Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        this.courierReference = required(courierReference, 160, "courierReference");
        this.assignedBy = required(assignedBy, 160, "assignedBy");
        this.createdAt = Objects.requireNonNull(now, "createdAt must not be null");
        this.updatedAt = now;
        this.status = DeliveryTaskStatus.ASSIGNED;
    }

    public static DeliveryTask assign(DeliveryTaskId id, TenantId tenantId, ShipmentId shipmentId,
                                      String courierReference, String assignedBy, Instant now) {
        return new DeliveryTask(id, tenantId, shipmentId, courierReference, assignedBy, now);
    }

    public void startDelivery(Instant now) {
        requireStatus(DeliveryTaskStatus.ASSIGNED);
        Instant next = monotonic(now);
        outForDeliveryAt = next;
        updatedAt = next;
        status = DeliveryTaskStatus.OUT_FOR_DELIVERY;
    }

    public void recordFailedAttempt(String reason, Instant now) {
        if (status != DeliveryTaskStatus.OUT_FOR_DELIVERY && status != DeliveryTaskStatus.DELIVERY_ATTEMPTED)
            throw new IllegalStateException("delivery attempt requires an active delivery");
        String normalizedReason = required(reason, 500, "failureReason");
        Instant next = monotonic(now);
        lastAttemptAt = next;
        failureReason = normalizedReason;
        updatedAt = next;
        status = DeliveryTaskStatus.DELIVERY_ATTEMPTED;
    }

    public void recordProofOfDelivery(String recipientName, String proofReference, Instant now) {
        if (status != DeliveryTaskStatus.OUT_FOR_DELIVERY && status != DeliveryTaskStatus.DELIVERY_ATTEMPTED)
            throw new IllegalStateException("proof of delivery requires an active delivery");
        String normalizedRecipient = required(recipientName, 160, "recipientName");
        String normalizedProof = required(proofReference, 512, "proofReference");
        Instant next = monotonic(now);
        this.recipientName = normalizedRecipient;
        this.proofReference = normalizedProof;
        deliveredAt = next;
        updatedAt = next;
        status = DeliveryTaskStatus.DELIVERED;
        failureReason = null;
    }

    public void fail(String reason, Instant now) {
        if (status != DeliveryTaskStatus.OUT_FOR_DELIVERY && status != DeliveryTaskStatus.DELIVERY_ATTEMPTED)
            throw new IllegalStateException("only an active delivery can be failed");
        String normalizedReason = required(reason, 500, "failureReason");
        Instant next = monotonic(now);
        failureReason = normalizedReason;
        updatedAt = next;
        status = DeliveryTaskStatus.FAILED;
    }

    public void cancel(Instant now) {
        if (status == DeliveryTaskStatus.CANCELLED) return;
        if (status != DeliveryTaskStatus.ASSIGNED)
            throw new IllegalStateException("only an unstarted assignment can be cancelled");
        Instant next = monotonic(now);
        updatedAt = next;
        status = DeliveryTaskStatus.CANCELLED;
    }

    public static DeliveryTask reconstitute(DeliveryTaskId id, TenantId tenantId, ShipmentId shipmentId,
            String courierReference, String assignedBy, DeliveryTaskStatus status, Instant createdAt,
            Instant updatedAt, Instant outForDeliveryAt, Instant lastAttemptAt, Instant deliveredAt,
            String recipientName, String proofReference, String failureReason) {
        DeliveryTask task = new DeliveryTask(id, tenantId, shipmentId, courierReference, assignedBy, createdAt);
        task.status = Objects.requireNonNull(status, "status must not be null");
        task.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not be before createdAt");
        validateTimestamp(outForDeliveryAt, createdAt, updatedAt, "outForDeliveryAt");
        validateTimestamp(lastAttemptAt, createdAt, updatedAt, "lastAttemptAt");
        validateTimestamp(deliveredAt, createdAt, updatedAt, "deliveredAt");
        if (outForDeliveryAt != null && lastAttemptAt != null && lastAttemptAt.isBefore(outForDeliveryAt))
            throw new IllegalArgumentException("lastAttemptAt must not be before outForDeliveryAt");
        if (deliveredAt != null && outForDeliveryAt == null)
            throw new IllegalArgumentException("deliveredAt requires outForDeliveryAt");
        if (status == DeliveryTaskStatus.FAILED && outForDeliveryAt == null)
            throw new IllegalArgumentException("failed delivery requires outForDeliveryAt");
        if (status != DeliveryTaskStatus.DELIVERED && deliveredAt != null)
            throw new IllegalArgumentException("only delivered tasks may have deliveredAt");
        if (status != DeliveryTaskStatus.DELIVERED && (recipientName != null || proofReference != null))
            throw new IllegalArgumentException("proof of delivery is only valid for delivered tasks");
        if (status == DeliveryTaskStatus.OUT_FOR_DELIVERY && outForDeliveryAt == null)
            throw new IllegalArgumentException("active delivery requires outForDeliveryAt");
        if (status == DeliveryTaskStatus.DELIVERY_ATTEMPTED && lastAttemptAt == null)
            throw new IllegalArgumentException("attempted delivery requires lastAttemptAt");
        if (status == DeliveryTaskStatus.DELIVERED
                && (deliveredAt == null || recipientName == null || proofReference == null))
            throw new IllegalArgumentException("delivered task requires recipient and proof of delivery");
        if ((status == DeliveryTaskStatus.FAILED || status == DeliveryTaskStatus.DELIVERY_ATTEMPTED)
                && (failureReason == null || failureReason.isBlank()))
            throw new IllegalArgumentException("failed outcome requires a reason");
        task.outForDeliveryAt = outForDeliveryAt;
        task.lastAttemptAt = lastAttemptAt;
        task.deliveredAt = deliveredAt;
        task.recipientName = blankToNull(recipientName);
        task.proofReference = blankToNull(proofReference);
        task.failureReason = blankToNull(failureReason);
        return task;
    }

    private static void validateTimestamp(Instant value, Instant createdAt, Instant updatedAt, String name) {
        if (value != null && (value.isBefore(createdAt) || value.isAfter(updatedAt)))
            throw new IllegalArgumentException(name + " must be between createdAt and updatedAt");
    }
    private Instant monotonic(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("time must not move backwards");
        return now;
    }
    private void requireStatus(DeliveryTaskStatus expected) {
        if (status != expected) throw new IllegalStateException("expected " + expected + " but was " + status);
    }
    private static String required(String value, int max, String field) {
        String result = Objects.requireNonNull(value, field + " must not be null").trim();
        if (result.isEmpty() || result.length() > max)
            throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
        return result;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public DeliveryTaskId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public ShipmentId shipmentId() { return shipmentId; }
    public String courierReference() { return courierReference; }
    public String assignedBy() { return assignedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public DeliveryTaskStatus status() { return status; }
    public Instant outForDeliveryAt() { return outForDeliveryAt; }
    public Instant lastAttemptAt() { return lastAttemptAt; }
    public Instant deliveredAt() { return deliveredAt; }
    public String recipientName() { return recipientName; }
    public String proofReference() { return proofReference; }
    public String failureReason() { return failureReason; }
}
