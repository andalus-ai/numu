package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.reservation.spi.DomainEventSink;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Writes reservation domain events to the reservation outbox using the caller's transaction-bound connection.
 *
 * <p>Construct this sink from {@link PgReservationTransactionRunner}'s connection factory, never from a pool.
 * Outbox rows therefore commit or roll back with reservation, idempotency, and availability-ledger writes.
 * Delivery is performed asynchronously by an outbox relay; this class never calls a broker.</p>
 */
public final class PgReservationOutboxEventSink implements DomainEventSink {
    private static final String INSERT_SQL = """
            INSERT INTO reservation_outbox_event
                (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload,
                 occurred_at, available_at)
            VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $7)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final SqlConnection connection;

    public PgReservationOutboxEventSink(SqlConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public Uni<Void> publish(UUID tenantId, List<DomainEvent> events) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        // Execute sequentially on the transaction connection; do not concurrently use one SQL connection.
        Uni<Void> writes = Uni.createFrom().voidItem();
        for (DomainEvent event : events) {
            Objects.requireNonNull(event, "events must not contain null");
            EventRow row = toRow(tenantId, event);
            writes = writes.chain(() -> connection.preparedQuery(INSERT_SQL)
                    .execute(Tuple.of(row.eventId(), row.tenantId(), row.aggregateType(), row.aggregateId(),
                            row.eventType(), row.payload(), offset(row.occurredAt())))
                    .replaceWithVoid());
        }
        return writes;
    }

    private static EventRow toRow(UUID tenantId, DomainEvent event) {
        JsonObject payload = JsonObject.mapFrom(event);
        boolean waitlistEvent = event.eventType().startsWith("reservation.waitlist.");
        String aggregateField = waitlistEvent ? "waitlistId" : "reservationId";
        String aggregateType = waitlistEvent ? "WaitlistEntry" : "Reservation";
        String aggregateValue = payload.getString(aggregateField);
        if (aggregateValue == null || aggregateValue.isBlank()) {
            throw new IllegalArgumentException("Unsupported reservation event payload: missing " + aggregateField
                    + " for event type " + event.eventType());
        }

        UUID aggregateId;
        try {
            aggregateId = UUID.fromString(aggregateValue);
        } catch (IllegalArgumentException invalidId) {
            throw new IllegalArgumentException("Invalid " + aggregateField + " in event " + event.eventType(),
                    invalidId);
        }
        return new EventRow(event.eventId(), tenantId, aggregateType, aggregateId,
                event.eventType(), payload.encode(), event.occurredAt());
    }

    private static OffsetDateTime offset(Instant instant) {
        return Objects.requireNonNull(instant, "event.occurredAt").atOffset(ZoneOffset.UTC);
    }

    private record EventRow(UUID eventId, UUID tenantId, String aggregateType, UUID aggregateId,
                            String eventType, String payload, Instant occurredAt) { }
}
