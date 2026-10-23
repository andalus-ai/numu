package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** PostgreSQL append-only tracking store with tenant isolation and idempotent ingestion. */
@ApplicationScoped
public class JdbcTrackingEventRepository implements TrackingEventRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcTrackingEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletionStage<TrackingEvent> append(TenantId tenantId, TrackingEvent event) {
        if (tenantId == null || event == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and event are required"));
        if (!tenantId.equals(event.tenantId()))
            return CompletableFuture.failedFuture(new SecurityException("tracking event tenant does not match repository tenant"));

        String insertSql = """
            INSERT INTO courier_tracking_events
              (tenant_id, id, shipment_id, parcel_id, event_type, occurred_at, recorded_at,
               location_code, actor_reference, source, idempotency_key, note)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
            """;
        try (Connection connection = dataSource.getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                    bind(statement, tenantId, event);
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    connection.commit();
                    return CompletableFuture.completedFuture(event);
                }

                TrackingEvent existing = findByIdempotencyKey(connection, tenantId, event.idempotencyKey())
                        .orElseThrow(() -> new IllegalStateException("idempotency conflict row disappeared"));
                if (!sameFact(existing, event)) {
                    connection.rollback();
                    return CompletableFuture.failedFuture(new IdempotencyConflictException(
                            "idempotency key was already used for a different tracking fact"));
                }
                connection.commit();
                return CompletableFuture.completedFuture(existing);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                return CompletableFuture.failedFuture(new IllegalStateException("Unable to append tracking event", failure));
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to open tracking transaction", failure));
        }
    }

    @Override
    public CompletionStage<List<TrackingEvent>> findByShipment(
            TenantId tenantId, ShipmentId shipmentId, Instant beforeOccurredAt, int limit) {
        if (tenantId == null || shipmentId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and shipmentId are required"));
        if (limit < 1 || limit > 500)
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit must be between 1 and 500"));

        String sql = """
            SELECT id, tenant_id, shipment_id, parcel_id, event_type, occurred_at, recorded_at,
                   location_code, actor_reference, source, idempotency_key, note
            FROM courier_tracking_events
            WHERE tenant_id = ? AND shipment_id = ? AND (? IS NULL OR occurred_at < ?)
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            statement.setObject(2, shipmentId.value());
            if (beforeOccurredAt == null) {
                statement.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
                statement.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setTimestamp(3, Timestamp.from(beforeOccurredAt));
                statement.setTimestamp(4, Timestamp.from(beforeOccurredAt));
            }
            statement.setInt(5, limit);
            List<TrackingEvent> events = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) events.add(map(rows));
            }
            return CompletableFuture.completedFuture(List.copyOf(events));
        } catch (SQLException | RuntimeException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to query tracking timeline", failure));
        }
    }

    private static Optional<TrackingEvent> findByIdempotencyKey(
            Connection connection, TenantId tenantId, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, tenant_id, shipment_id, parcel_id, event_type, occurred_at, recorded_at,
                       location_code, actor_reference, source, idempotency_key, note
                FROM courier_tracking_events WHERE tenant_id = ? AND idempotency_key = ?
                """)) {
            statement.setObject(1, tenantId.value());
            statement.setString(2, key);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        }
    }

    private static boolean sameFact(TrackingEvent a, TrackingEvent b) {
        return a.shipmentId().equals(b.shipmentId())
                && java.util.Objects.equals(a.parcelId(), b.parcelId())
                && a.type() == b.type()
                && a.occurredAt().equals(b.occurredAt())
                && java.util.Objects.equals(a.locationCode(), b.locationCode())
                && java.util.Objects.equals(a.actorReference(), b.actorReference())
                && a.source().equals(b.source())
                && java.util.Objects.equals(a.note(), b.note());
    }

    private static void bind(PreparedStatement s, TenantId tenantId, TrackingEvent e) throws SQLException {
        s.setObject(1, tenantId.value());
        s.setObject(2, e.id().value());
        s.setObject(3, e.shipmentId().value());
        if (e.parcelId() == null) s.setNull(4, Types.OTHER); else s.setObject(4, e.parcelId().value());
        s.setString(5, e.type().name());
        s.setTimestamp(6, Timestamp.from(e.occurredAt()));
        s.setTimestamp(7, Timestamp.from(e.recordedAt()));
        s.setString(8, e.locationCode());
        s.setString(9, e.actorReference());
        s.setString(10, e.source().toUpperCase(Locale.ROOT));
        s.setString(11, e.idempotencyKey());
        s.setString(12, e.note());
    }

    private static TrackingEvent map(ResultSet row) throws SQLException {
        java.util.UUID parcelId = row.getObject("parcel_id", java.util.UUID.class);
        return new TrackingEvent(
                TrackingEventId.of(row.getObject("id", java.util.UUID.class)),
                TenantId.of(row.getObject("tenant_id", java.util.UUID.class)),
                ShipmentId.of(row.getObject("shipment_id", java.util.UUID.class)),
                parcelId == null ? null : ParcelId.of(parcelId),
                TrackingEventType.valueOf(row.getString("event_type")),
                row.getTimestamp("occurred_at").toInstant(),
                row.getTimestamp("recorded_at").toInstant(),
                row.getString("location_code"), row.getString("actor_reference"),
                row.getString("source"), row.getString("idempotency_key"), row.getString("note"));
    }

    public static final class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
