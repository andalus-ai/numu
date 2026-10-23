package tech.kayys.numou.fulfillment.application;

import tech.kayys.numou.fulfillment.domain.FulfillmentLine;
import tech.kayys.numou.fulfillment.domain.FulfillmentOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable integration event contract; persist it in the aggregate transaction (outbox). */
public record FulfillmentOutboxEvent(
        String eventId,
        String tenantId,
        String aggregateId,
        long aggregateVersion,
        String eventType,
        String payload,
        Instant occurredAt
) {
    public FulfillmentOutboxEvent {
        eventId = requireText(eventId, "eventId");
        tenantId = requireText(tenantId, "tenantId");
        aggregateId = requireText(aggregateId, "aggregateId");
        eventType = requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (aggregateVersion < 1) throw new IllegalArgumentException("aggregateVersion must be positive");
    }

    public static FulfillmentOutboxEvent stateChanged(String tenantId, String aggregateId, long version,
                                                       String previousStatus, String nextStatus) {
        String payload = "{\"previousStatus\":\"" + json(previousStatus)
                + "\",\"nextStatus\":\"" + json(nextStatus) + "\"}";
        return new FulfillmentOutboxEvent(UUID.randomUUID().toString(), tenantId, aggregateId, version,
                "fulfillment.state-changed.v1", payload, Instant.now());
    }

    /** Carries requested lines so a downstream allocation workflow can reserve stock. */
    public static FulfillmentOutboxEvent requested(FulfillmentOrder order) {
        StringBuilder payload = new StringBuilder("{\"externalReference\":\"")
                .append(json(order.externalReference())).append("\",\"lines\":[");
        for (int i = 0; i < order.lines().size(); i++) {
            FulfillmentLine line = order.lines().get(i);
            if (i > 0) payload.append(',');
            payload.append("{\"lineId\":\"").append(json(line.lineId()))
                    .append("\",\"itemReference\":\"").append(json(line.itemReference()))
                    .append("\",\"quantity\":\"").append(json(decimal(line.requestedQuantity())))
                    .append("\",\"uom\":\"").append(json(line.uom())).append("\"}");
        }
        payload.append("]}");
        return new FulfillmentOutboxEvent(UUID.randomUUID().toString(), order.tenantId(), order.id().value(),
                order.version(), "fulfillment.requested.v1", payload.toString(), Instant.now());
    }

    public static FulfillmentOutboxEvent shipmentHandoffRequested(String tenantId, String aggregateId,
                                                                  long version, String shipmentReference) {
        return new FulfillmentOutboxEvent(UUID.randomUUID().toString(), tenantId, aggregateId, version,
                "fulfillment.shipment-handoff-requested.v1",
                "{\"shipmentReference\":\"" + json(shipmentReference) + "\"}", Instant.now());
    }

    public static FulfillmentOutboxEvent cancelled(String tenantId, String aggregateId, long version) {
        return new FulfillmentOutboxEvent(UUID.randomUUID().toString(), tenantId, aggregateId, version,
                "fulfillment.cancelled.v1", "{}", Instant.now());
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
