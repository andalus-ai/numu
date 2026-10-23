package tech.kayys.numou.fulfillment.application;

import tech.kayys.numou.fulfillment.domain.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant-scoped fulfillment command boundary with request fingerprints,
 * optimistic concurrency, and outbox events. Allocation is confirmed only
 * after an inventory workflow reports that its reservation succeeded.
 */
public final class FulfillmentApplicationService {
    private final FulfillmentRepository repository;

    public FulfillmentApplicationService(FulfillmentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Fulfillment repository is required");
    }

    public FulfillmentOrder request(String tenantId, FulfillmentCommandKey key,
                                    String externalReference, List<FulfillmentLine> lines) {
        String tenant = requireTenant(tenantId);
        String reference = requireText(externalReference, "externalReference");
        Objects.requireNonNull(lines, "Fulfillment lines are required");
        List<FulfillmentLine> requestedLines = List.copyOf(lines);
        String fingerprint = fingerprint("request", tenant, reference,
                requestedLines.stream().map(FulfillmentApplicationService::lineCanonical).toList());
        Optional<FulfillmentCommandReceipt> replay = replay(tenant, key, fingerprint);
        if (replay.isPresent()) return replay.get().result();

        FulfillmentOrder order = FulfillmentOrder.request(tenant, reference, requestedLines);
        return repository.commit(tenant, 0, order, key, fingerprint,
                List.of(FulfillmentOutboxEvent.requested(order))).order();
    }

    /** Call only after inventory confirms a reservation/allocation for this quantity. */
    public FulfillmentOrder confirmAllocation(String tenantId, FulfillmentOrderId orderId,
                                              String lineId, BigDecimal quantity, String warehouseReference,
                                              FulfillmentCommandKey key) {
        String warehouse = requireText(warehouseReference, "warehouseReference");
        return mutate(tenantId, orderId, key, "confirm-allocation",
                List.of(requireText(lineId, "lineId"), quantityCanonical(quantity), warehouse),
                order -> order.allocate(lineId, quantity));
    }

    public FulfillmentOrder confirmPick(String tenantId, FulfillmentOrderId orderId, String lineId,
                                        BigDecimal quantity, FulfillmentCommandKey key) {
        return mutate(tenantId, orderId, key, "confirm-pick",
                List.of(requireText(lineId, "lineId"), quantityCanonical(quantity)), order -> order.pick(lineId, quantity));
    }

    public FulfillmentOrder confirmPack(String tenantId, FulfillmentOrderId orderId, String lineId,
                                        BigDecimal quantity, FulfillmentCommandKey key) {
        return mutate(tenantId, orderId, key, "confirm-pack",
                List.of(requireText(lineId, "lineId"), quantityCanonical(quantity)), order -> order.pack(lineId, quantity));
    }

    public FulfillmentOrder stage(String tenantId, FulfillmentOrderId orderId, FulfillmentCommandKey key) {
        return mutate(tenantId, orderId, key, "stage", List.of(), FulfillmentOrder::stage);
    }

    /** Requests handoff; the outbox event is committed atomically with HANDOFF_REQUESTED. */
    public FulfillmentOrder requestShipmentHandoff(String tenantId, FulfillmentOrderId orderId,
                                                   String shipmentReference, FulfillmentCommandKey key) {
        String shipment = requireText(shipmentReference, "shipmentReference");
        return mutate(tenantId, orderId, key, "request-shipment-handoff", List.of(shipment),
                order -> order.requestShipmentHandoff(shipment));
    }

    /** Call only after the warehouse/carrier integration confirms physical handoff. */
    public FulfillmentOrder confirmShipped(String tenantId, FulfillmentOrderId orderId,
                                           String shipmentReference, FulfillmentCommandKey key) {
        String shipment = requireText(shipmentReference, "shipmentReference");
        return mutate(tenantId, orderId, key, "confirm-shipped", List.of(shipment),
                order -> order.ship(shipment));
    }

    public FulfillmentOrder deliver(String tenantId, FulfillmentOrderId orderId, FulfillmentCommandKey key) {
        return mutate(tenantId, orderId, key, "deliver", List.of(), FulfillmentOrder::deliver);
    }

    public FulfillmentOrder cancel(String tenantId, FulfillmentOrderId orderId, FulfillmentCommandKey key) {
        return mutate(tenantId, orderId, key, "cancel", List.of(), FulfillmentOrder::cancel);
    }

    public FulfillmentOrder reject(String tenantId, FulfillmentOrderId orderId, FulfillmentCommandKey key) {
        return mutate(tenantId, orderId, key, "reject", List.of(), FulfillmentOrder::reject);
    }

    private FulfillmentOrder mutate(String tenantId, FulfillmentOrderId orderId, FulfillmentCommandKey key,
                                    String commandType, List<String> args,
                                    java.util.function.UnaryOperator<FulfillmentOrder> transition) {
        String tenant = requireTenant(tenantId);
        Objects.requireNonNull(orderId, "Order id is required");
        String fingerprint = fingerprint(commandType, tenant, orderId.value(), args);
        Optional<FulfillmentCommandReceipt> replay = replay(tenant, key, fingerprint);
        if (replay.isPresent()) return replay.get().result();

        FulfillmentOrder current = repository.find(tenant, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Fulfillment order not found for tenant"));
        FulfillmentOrder next = transition.apply(current);
        java.util.ArrayList<FulfillmentOutboxEvent> events = new java.util.ArrayList<>();
        events.add(FulfillmentOutboxEvent.stateChanged(tenant, orderId.value(), next.version(),
                current.status().name(), next.status().name()));
        if (commandType.equals("request-shipment-handoff")) {
            events.add(FulfillmentOutboxEvent.shipmentHandoffRequested(tenant, orderId.value(),
                    next.version(), next.shipmentReference()));
        }
        if (commandType.equals("cancel")) {
            events.add(FulfillmentOutboxEvent.cancelled(tenant, orderId.value(), next.version()));
        }
        return repository.commit(tenant, current.version(), next, key, fingerprint, List.copyOf(events)).order();
    }

    private Optional<FulfillmentCommandReceipt> replay(String tenant, FulfillmentCommandKey key, String fingerprint) {
        Objects.requireNonNull(key, "Command key is required");
        Optional<FulfillmentCommandReceipt> existing = repository.findReceipt(tenant, key);
        if (existing.isEmpty()) return Optional.empty();
        if (!existing.get().requestFingerprint().equals(fingerprint)) {
            throw new FulfillmentIdempotencyConflictException("Command key was already used with a different request");
        }
        return existing;
    }

    private static String fingerprint(String command, String tenant, String aggregate, List<String> args) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, command);
        append(canonical, tenant);
        append(canonical, aggregate);
        for (String arg : args) append(canonical, arg);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static String lineCanonical(FulfillmentLine line) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, line.lineId());
        append(canonical, line.itemReference());
        append(canonical, quantityCanonical(line.requestedQuantity()));
        append(canonical, line.uom());
        return canonical.toString();
    }

    private static String quantityCanonical(BigDecimal value) {
        Objects.requireNonNull(value, "Quantity is required");
        return value.stripTrailingZeros().toPlainString();
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static String requireTenant(String value) {
        String tenant = requireText(value, "tenantId");
        if (tenant.length() > 80 || tenant.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Tenant id must be at most 80 characters and must not contain ':'");
        }
        return tenant;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
