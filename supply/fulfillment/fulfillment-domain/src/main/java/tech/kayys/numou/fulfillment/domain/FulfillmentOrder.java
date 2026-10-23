package tech.kayys.numou.fulfillment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate for demand fulfillment. It owns lifecycle and quantity invariants;
 * it does not post inventory movements or call a carrier directly.
 */
public record FulfillmentOrder(
        FulfillmentOrderId id,
        String tenantId,
        String externalReference,
        FulfillmentStatus status,
        List<FulfillmentLine> lines,
        String shipmentReference,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public FulfillmentOrder {
        Objects.requireNonNull(id, "id is required");
        tenantId = requireText(tenantId, "tenantId");
        externalReference = requireText(externalReference, "externalReference");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(lines, "lines are required");
        lines = List.copyOf(lines);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (lines.isEmpty()) throw new IllegalArgumentException("Fulfillment order must contain at least one line");
        if (version < 1) throw new IllegalArgumentException("Version must be positive");
        Set<String> lineIds = new HashSet<>();
        for (FulfillmentLine line : lines) {
            Objects.requireNonNull(line, "Fulfillment lines cannot contain null");
            if (!lineIds.add(line.lineId())) throw new IllegalArgumentException("Duplicate fulfillment line id: " + line.lineId());
        }
        if (status == FulfillmentStatus.HANDOFF_REQUESTED || status == FulfillmentStatus.SHIPPED
                || status == FulfillmentStatus.DELIVERED) {
            requireText(shipmentReference, "shipmentReference");
        }
    }

    public static FulfillmentOrder request(String tenantId, String externalReference, List<FulfillmentLine> lines) {
        Instant now = Instant.now();
        return new FulfillmentOrder(FulfillmentOrderId.generate(), tenantId, externalReference,
                FulfillmentStatus.REQUESTED, lines, null, now, now, 1);
    }

    public FulfillmentOrder allocate(String lineId, BigDecimal quantity) {
        requireMutable(FulfillmentStatus.REQUESTED, FulfillmentStatus.PARTIALLY_ALLOCATED, FulfillmentStatus.ALLOCATED);
        List<FulfillmentLine> next = replaceLine(lineId, line -> line.allocate(quantity));
        FulfillmentStatus nextStatus = total(next, Quantity.ALLOCATED).compareTo(total(next, Quantity.REQUESTED)) == 0
                ? FulfillmentStatus.ALLOCATED : FulfillmentStatus.PARTIALLY_ALLOCATED;
        return revise(nextStatus, next, shipmentReference);
    }

    public FulfillmentOrder pick(String lineId, BigDecimal quantity) {
        requireMutable(FulfillmentStatus.PARTIALLY_ALLOCATED, FulfillmentStatus.ALLOCATED, FulfillmentStatus.PARTIALLY_PICKED);
        List<FulfillmentLine> next = replaceLine(lineId, line -> line.pick(quantity));
        FulfillmentStatus nextStatus = total(next, Quantity.PICKED).compareTo(total(next, Quantity.ALLOCATED)) == 0
                ? FulfillmentStatus.PICKED : FulfillmentStatus.PARTIALLY_PICKED;
        return revise(nextStatus, next, shipmentReference);
    }

    public FulfillmentOrder pack(String lineId, BigDecimal quantity) {
        requireMutable(FulfillmentStatus.PICKED, FulfillmentStatus.PARTIALLY_PACKED);
        List<FulfillmentLine> next = replaceLine(lineId, line -> line.pack(quantity));
        FulfillmentStatus nextStatus = total(next, Quantity.PACKED).compareTo(total(next, Quantity.PICKED)) == 0
                ? FulfillmentStatus.PACKED : FulfillmentStatus.PARTIALLY_PACKED;
        return revise(nextStatus, next, shipmentReference);
    }

    public FulfillmentOrder stage() {
        requireStatus(FulfillmentStatus.PACKED);
        return revise(FulfillmentStatus.STAGED, lines, shipmentReference);
    }

    /** Requests carrier/dispatch handoff; the order is not marked shipped yet. */
    public FulfillmentOrder requestShipmentHandoff(String shipmentReference) {
        requireStatus(FulfillmentStatus.STAGED);
        return revise(FulfillmentStatus.HANDOFF_REQUESTED, lines,
                requireText(shipmentReference, "shipmentReference"));
    }

    /** Confirms that the physical carrier handoff has occurred. */
    public FulfillmentOrder ship(String shipmentReference) {
        requireStatus(FulfillmentStatus.HANDOFF_REQUESTED);
        String requestedReference = requireText(this.shipmentReference, "shipmentReference");
        String confirmedReference = requireText(shipmentReference, "shipmentReference");
        if (!requestedReference.equals(confirmedReference)) {
            throw new IllegalArgumentException("Shipment reference must match the requested handoff");
        }
        return revise(FulfillmentStatus.SHIPPED, lines, confirmedReference);
    }

    public FulfillmentOrder deliver() {
        requireStatus(FulfillmentStatus.SHIPPED);
        return revise(FulfillmentStatus.DELIVERED, lines, shipmentReference);
    }

    public FulfillmentOrder cancel() {
        if (status == FulfillmentStatus.HANDOFF_REQUESTED || status == FulfillmentStatus.SHIPPED
                || status == FulfillmentStatus.DELIVERED || status == FulfillmentStatus.CANCELLED
                || status == FulfillmentStatus.REJECTED) {
            throw new IllegalStateException("Cannot cancel fulfillment order in status " + status);
        }
        return revise(FulfillmentStatus.CANCELLED, lines, shipmentReference);
    }

    public FulfillmentOrder reject() {
        requireStatus(FulfillmentStatus.REQUESTED);
        return revise(FulfillmentStatus.REJECTED, lines, shipmentReference);
    }

    private List<FulfillmentLine> replaceLine(String lineId, java.util.function.UnaryOperator<FulfillmentLine> change) {
        String normalizedId = requireText(lineId, "lineId");
        boolean found = false;
        java.util.ArrayList<FulfillmentLine> next = new java.util.ArrayList<>(lines.size());
        for (FulfillmentLine line : lines) {
            if (line.lineId().equals(normalizedId)) {
                next.add(change.apply(line));
                found = true;
            } else {
                next.add(line);
            }
        }
        if (!found) throw new IllegalArgumentException("Unknown fulfillment line: " + normalizedId);
        return List.copyOf(next);
    }

    private void requireMutable(FulfillmentStatus... allowed) {
        for (FulfillmentStatus candidate : allowed) if (status == candidate) return;
        throw new IllegalStateException("Operation is not allowed in fulfillment status " + status);
    }

    private void requireStatus(FulfillmentStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected " + expected + " but was " + status);
    }

    private FulfillmentOrder revise(FulfillmentStatus nextStatus, List<FulfillmentLine> nextLines, String nextShipmentReference) {
        return new FulfillmentOrder(id, tenantId, externalReference, nextStatus, nextLines, nextShipmentReference,
                createdAt, Instant.now(), version + 1);
    }

    private static BigDecimal total(List<FulfillmentLine> lines, Quantity quantity) {
        return lines.stream().map(line -> switch (quantity) {
            case REQUESTED -> line.requestedQuantity();
            case ALLOCATED -> line.allocatedQuantity();
            case PICKED -> line.pickedQuantity();
            case PACKED -> line.packedQuantity();
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private enum Quantity { REQUESTED, ALLOCATED, PICKED, PACKED }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
