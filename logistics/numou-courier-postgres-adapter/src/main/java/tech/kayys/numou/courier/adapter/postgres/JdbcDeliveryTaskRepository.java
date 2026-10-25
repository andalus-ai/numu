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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped JDBC persistence for courier assignments and POD outcomes. */
@ApplicationScoped
public class JdbcDeliveryTaskRepository implements DeliveryTaskRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcDeliveryTaskRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public CompletionStage<DeliveryTask> save(TenantId tenantId, DeliveryTask task) {
        if (tenantId == null || task == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and task are required"));
        if (!tenantId.equals(task.tenantId()))
            return CompletableFuture.failedFuture(new SecurityException("delivery task tenant does not match repository tenant"));

        String sql = """
            INSERT INTO courier_delivery_tasks
              (tenant_id, id, shipment_id, delivery_attempt_reference, courier_reference, assigned_by, status, created_at, updated_at,
               out_for_delivery_at, last_attempt_at, delivered_at, recipient_name, proof_reference, failure_reason)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (tenant_id, id) DO UPDATE SET
              status=EXCLUDED.status, updated_at=EXCLUDED.updated_at,
              out_for_delivery_at=EXCLUDED.out_for_delivery_at, last_attempt_at=EXCLUDED.last_attempt_at,
              delivered_at=EXCLUDED.delivered_at, recipient_name=EXCLUDED.recipient_name,
              proof_reference=EXCLUDED.proof_reference, failure_reason=EXCLUDED.failure_reason
            WHERE courier_delivery_tasks.shipment_id = EXCLUDED.shipment_id
              AND (courier_delivery_tasks.status = EXCLUDED.status
                OR (courier_delivery_tasks.status = 'ASSIGNED' AND EXCLUDED.status IN ('OUT_FOR_DELIVERY', 'CANCELLED'))
                OR (courier_delivery_tasks.status = 'OUT_FOR_DELIVERY' AND EXCLUDED.status IN ('DELIVERY_ATTEMPTED', 'DELIVERED', 'FAILED'))
                OR (courier_delivery_tasks.status = 'DELIVERY_ATTEMPTED' AND EXCLUDED.status IN ('DELIVERY_ATTEMPTED', 'DELIVERED', 'FAILED')))
            """;
        try (Connection connection = dataSource.getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                DeliveryTaskStatus previousStatus = null;
                Instant previousUpdatedAt = null;
                try (PreparedStatement existing = connection.prepareStatement(
                        "SELECT status, updated_at FROM courier_delivery_tasks WHERE tenant_id = ? AND id = ? FOR UPDATE")) {
                    existing.setObject(1, tenantId.value());
                    existing.setObject(2, task.id().value());
                    try (ResultSet row = existing.executeQuery()) {
                        if (row.next()) {
                            previousStatus = DeliveryTaskStatus.valueOf(row.getString("status"));
                            previousUpdatedAt = row.getTimestamp("updated_at").toInstant();
                        }
                    }
                }
                try (PreparedStatement shipment = connection.prepareStatement(
                        "SELECT status FROM courier_shipments WHERE tenant_id = ? AND id = ? FOR SHARE")) {
                    shipment.setObject(1, tenantId.value());
                    shipment.setObject(2, task.shipmentId().value());
                    try (ResultSet row = shipment.executeQuery()) {
                        if (!row.next()) throw new IllegalArgumentException("shipment does not exist in this tenant");
                        if (!"BOOKED".equals(row.getString("status")))
                            throw new IllegalStateException("only booked shipments may be assigned for delivery");
                    }
                }
                int affected;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, task.id().value());
                    statement.setObject(3, task.shipmentId().value());
                    statement.setString(4, attemptReference(task.id()));
                    statement.setString(5, task.courierReference());
                    statement.setString(6, task.assignedBy());
                    statement.setString(7, task.status().name());
                    statement.setTimestamp(8, Timestamp.from(task.createdAt()));
                    statement.setTimestamp(9, Timestamp.from(task.updatedAt()));
                    setInstant(statement, 10, task.outForDeliveryAt());
                    setInstant(statement, 11, task.lastAttemptAt());
                    setInstant(statement, 12, task.deliveredAt());
                    statement.setString(13, task.recipientName());
                    statement.setString(14, task.proofReference());
                    statement.setString(15, task.failureReason());
                    affected = statement.executeUpdate();
                }
                if (affected == 0) throw new IllegalStateException("delivery task lifecycle regression rejected");
                boolean statusChanged = previousStatus != null && previousStatus != task.status();
                boolean repeatedAttempt = previousStatus == DeliveryTaskStatus.DELIVERY_ATTEMPTED
                        && task.status() == DeliveryTaskStatus.DELIVERY_ATTEMPTED
                        && previousUpdatedAt != null && task.updatedAt().isAfter(previousUpdatedAt);
                if (statusChanged || repeatedAttempt) appendOutbox(connection, task);
                connection.commit();
                return CompletableFuture.completedFuture(task);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                return CompletableFuture.failedFuture(new IllegalStateException("Unable to save delivery task", failure));
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to open delivery task transaction", failure));
        }
    }

    @Override
    public CompletionStage<Optional<DeliveryTask>> findById(TenantId tenantId, DeliveryTaskId taskId) {
        if (tenantId == null || taskId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and taskId are required"));
        return findMany("tenant_id = ? AND id = ?", statement -> {
            statement.setObject(1, tenantId.value()); statement.setObject(2, taskId.value());
        }, 1).thenApply(rows -> rows.stream().findFirst());
    }

    @Override
    public CompletionStage<List<DeliveryTask>> findByCourier(TenantId tenantId, String courierReference, int limit) {
        if (tenantId == null || courierReference == null || courierReference.isBlank())
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and courierReference are required"));
        if (limit < 1 || limit > 500)
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit must be between 1 and 500"));
        return findMany("tenant_id = ? AND courier_reference = ?", statement -> {
            statement.setObject(1, tenantId.value()); statement.setString(2, courierReference.trim());
        }, limit);
    }

    @Override
    public CompletionStage<Optional<DeliveryTask>> findActiveByShipment(TenantId tenantId, ShipmentId shipmentId) {
        if (tenantId == null || shipmentId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and shipmentId are required"));
        return findMany("tenant_id = ? AND shipment_id = ? AND status IN ('ASSIGNED','OUT_FOR_DELIVERY','DELIVERY_ATTEMPTED')",
                statement -> { statement.setObject(1, tenantId.value()); statement.setObject(2, shipmentId.value()); }, 1)
                .thenApply(rows -> rows.stream().findFirst());
    }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }

    private CompletionStage<List<DeliveryTask>> findMany(String predicate, Binder binder, int limit) {
        String sql = "SELECT * FROM courier_delivery_tasks WHERE " + predicate + " ORDER BY created_at DESC, id DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.setInt(parameterCount(predicate) + 1, limit);
            List<DeliveryTask> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(map(rows));
            }
            return CompletableFuture.completedFuture(List.copyOf(result));
        } catch (SQLException | RuntimeException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to query delivery tasks", failure));
        }
    }

    private static int parameterCount(String predicate) {
        int count = 0;
        for (int i = 0; i < predicate.length(); i++) if (predicate.charAt(i) == '?') count++;
        return count;
    }


    /** Stable POD attempt identity shared with external proof-of-delivery submissions. */
    static String attemptReference(DeliveryTaskId taskId) {
        return "TASK:" + taskId.value();
    }

    private static void appendOutbox(Connection connection, DeliveryTask task) throws SQLException {
        String eventType = switch (task.status()) {
            case OUT_FOR_DELIVERY -> "OUT_FOR_DELIVERY";
            case DELIVERY_ATTEMPTED -> "DELIVERY_ATTEMPTED";
            case DELIVERED -> "DELIVERED";
            case FAILED -> "DELIVERY_FAILED";
            case CANCELLED -> "DELIVERY_CANCELLED";
            case ASSIGNED -> throw new IllegalStateException("assignment creation is not a tracking milestone");
        };
        String eventKey = task.id() + ":" + task.status().name() + ":" + task.updatedAt();
        String payload = "{\"deliveryTaskId\":\"" + task.id()
                + "\",\"shipmentId\":\"" + task.shipmentId()
                + "\",\"courierReference\":\"" + jsonEscape(task.courierReference())
                + "\",\"status\":\"" + task.status().name()
                + "\",\"occurredAt\":\"" + task.updatedAt()
                + "\",\"recipientName\":" + jsonNullable(task.recipientName())
                + ",\"proofReference\":" + jsonNullable(task.proofReference())
                + ",\"failureReason\":" + jsonNullable(task.failureReason()) + "}";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO courier_operational_outbox
                  (tenant_id, id, aggregate_type, aggregate_id, shipment_id, event_type, event_key, occurred_at, payload)
                VALUES (?, ?, 'DELIVERY_TASK', ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (tenant_id, event_key) DO NOTHING
                """)) {
            statement.setObject(1, task.tenantId().value());
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, task.id().value());
            statement.setObject(4, task.shipmentId().value());
            statement.setString(5, eventType);
            statement.setString(6, eventKey);
            statement.setTimestamp(7, Timestamp.from(task.updatedAt()));
            statement.setString(8, payload);
            statement.executeUpdate();
        }
    }

    private static String jsonNullable(String value) {
        return value == null ? "null" : "\"" + jsonEscape(value) + "\"";
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static DeliveryTask map(ResultSet row) throws SQLException {
        return DeliveryTask.reconstitute(
                DeliveryTaskId.of(row.getObject("id", java.util.UUID.class)),
                TenantId.of(row.getObject("tenant_id", java.util.UUID.class)),
                ShipmentId.of(row.getObject("shipment_id", java.util.UUID.class)),
                row.getString("courier_reference"), row.getString("assigned_by"),
                DeliveryTaskStatus.valueOf(row.getString("status")),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
                instant(row, "out_for_delivery_at"), instant(row, "last_attempt_at"), instant(row, "delivered_at"),
                row.getString("recipient_name"), row.getString("proof_reference"), row.getString("failure_reason"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, Timestamp.from(value));
    }
}
