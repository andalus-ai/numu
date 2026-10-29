package tech.kayys.numou.pos.application.event;

import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;
import tech.kayys.numou.pos.domain.Sale;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps committed sale lifecycle states to versioned integration-event envelopes.
 *
 * <p>Call only when a command creates a real state transition, and append the
 * returned event through {@code PosOutboxWriter} in the same transaction.</p>
 */
public final class SaleLifecycleEvents {
    public static final int SCHEMA_VERSION = 1;
    public static final String AGGREGATE_TYPE = "Sale";

    private SaleLifecycleEvents() {
    }

    public static PosOutboxEvent opened(Sale sale) {
        Objects.requireNonNull(sale, "sale cannot be null");
        if (sale.status() != Sale.Status.OPEN || !sale.lines().isEmpty()) {
            throw new IllegalArgumentException("opened event requires a newly opened, empty sale");
        }
        return event(sale, "pos.sale.opened", sale.openedAt(),
                "\"saleId\":\"" + sale.id()
                        + "\",\"branchId\":\"" + sale.branchId()
                        + "\",\"registerId\":\"" + sale.registerId()
                        + "\",\"shiftId\":\"" + sale.shiftId()
                        + "\",\"channel\":\"" + sale.channel()
                        + "\",\"currency\":\"" + sale.currency()
                        + "\",\"openedAt\":\"" + sale.openedAt() + "\"");
    }

    public static PosOutboxEvent completed(Sale sale) {
        Objects.requireNonNull(sale, "sale cannot be null");
        if (sale.status() != Sale.Status.COMPLETED) {
            throw new IllegalArgumentException("completed event requires a completed sale");
        }
        return event(sale, "pos.sale.completed", sale.completedAt(),
                "\"saleId\":\"" + sale.id()
                        + "\",\"branchId\":\"" + sale.branchId()
                        + "\",\"registerId\":\"" + sale.registerId()
                        + "\",\"shiftId\":\"" + sale.shiftId()
                        + "\",\"channel\":\"" + sale.channel()
                        + "\",\"currency\":\"" + sale.currency()
                        + "\",\"lineCount\":" + sale.lines().size()
                        + ",\"completedAt\":\"" + sale.completedAt() + "\"");
    }

    public static PosOutboxEvent voided(Sale sale, Instant occurredAt) {
        Objects.requireNonNull(sale, "sale cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (sale.status() != Sale.Status.VOIDED) {
            throw new IllegalArgumentException("voided event requires a voided sale");
        }
        return event(sale, "pos.sale.voided", occurredAt,
                "\"saleId\":\"" + sale.id()
                        + "\",\"branchId\":\"" + sale.branchId()
                        + "\",\"registerId\":\"" + sale.registerId()
                        + "\",\"shiftId\":\"" + sale.shiftId()
                        + "\",\"channel\":\"" + sale.channel()
                        + "\",\"currency\":\"" + sale.currency()
                        + "\",\"openedAt\":\"" + sale.openedAt()
                        + "\",\"voidedAt\":\"" + occurredAt + "\"");
    }

    private static PosOutboxEvent event(Sale sale, String type, Instant occurredAt, String fields) {
        String payload = "{\"schemaVersion\":" + SCHEMA_VERSION + "," + fields + "}";
        return new PosOutboxEvent(
                UUID.randomUUID(),
                sale.tenantId(),
                AGGREGATE_TYPE,
                sale.id().toString(),
                type,
                SCHEMA_VERSION,
                occurredAt,
                payload);
    }
}
