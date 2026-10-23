package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Atomic JDBC persistence for tenant-scoped hub transfer manifests and their shipment membership. */
@ApplicationScoped
public class JdbcHubTransferRepository implements HubTransferRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcHubTransferRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletionStage<HubTransfer> save(TenantId tenantId, HubTransfer transfer) {
        if (tenantId == null || transfer == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and transfer are required"));
        if (!tenantId.equals(transfer.tenantId()))
            return CompletableFuture.failedFuture(new SecurityException("transfer tenant does not match repository tenant"));

        String sql = """
            INSERT INTO courier_hub_transfers
              (tenant_id, id, reference, normalized_reference, origin_hub_id, destination_hub_id,
               status, created_at, updated_at, departed_at, arrived_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (tenant_id, id) DO UPDATE SET
              reference=EXCLUDED.reference, normalized_reference=EXCLUDED.normalized_reference,
              origin_hub_id=EXCLUDED.origin_hub_id, destination_hub_id=EXCLUDED.destination_hub_id,
              status=EXCLUDED.status, updated_at=EXCLUDED.updated_at,
              departed_at=EXCLUDED.departed_at, arrived_at=EXCLUDED.arrived_at
            WHERE courier_hub_transfers.status = EXCLUDED.status
               OR (courier_hub_transfers.status = 'DRAFT' AND EXCLUDED.status IN ('SEALED', 'CANCELLED'))
               OR (courier_hub_transfers.status = 'SEALED' AND EXCLUDED.status IN ('DEPARTED', 'CANCELLED'))
               OR (courier_hub_transfers.status = 'DEPARTED' AND EXCLUDED.status = 'ARRIVED')
            """;
        try (Connection connection = dataSource.getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int affected;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, tenantId.value());
                    statement.setObject(2, transfer.id().value());
                    statement.setString(3, transfer.reference());
                    statement.setString(4, transfer.reference().trim().toUpperCase(Locale.ROOT));
                    statement.setObject(5, transfer.originHubId().value());
                    statement.setObject(6, transfer.destinationHubId().value());
                    statement.setString(7, transfer.status().name());
                    statement.setTimestamp(8, Timestamp.from(transfer.createdAt()));
                    statement.setTimestamp(9, Timestamp.from(transfer.updatedAt()));
                    if (transfer.departedAt() == null) statement.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
                    else statement.setTimestamp(10, Timestamp.from(transfer.departedAt()));
                    if (transfer.arrivedAt() == null) statement.setNull(11, Types.TIMESTAMP_WITH_TIMEZONE);
                    else statement.setTimestamp(11, Timestamp.from(transfer.arrivedAt()));
                    affected = statement.executeUpdate();
                }
                if (affected == 0) throw new IllegalStateException("transfer lifecycle regression rejected");
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM courier_hub_transfer_shipments WHERE tenant_id = ? AND transfer_id = ?")) {
                    delete.setObject(1, tenantId.value());
                    delete.setObject(2, transfer.id().value());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO courier_hub_transfer_shipments (tenant_id, transfer_id, shipment_id)
                        VALUES (?, ?, ?)
                        """)) {
                    for (ShipmentId shipmentId : transfer.shipments()) {
                        insert.setObject(1, tenantId.value());
                        insert.setObject(2, transfer.id().value());
                        insert.setObject(3, shipmentId.value());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
                return CompletableFuture.completedFuture(transfer);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                return CompletableFuture.failedFuture(new IllegalStateException("Unable to save hub transfer", failure));
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to open hub transfer transaction", failure));
        }
    }

    @Override
    public CompletionStage<Optional<HubTransfer>> findById(TenantId tenantId, HubTransferId transferId) {
        if (tenantId == null || transferId == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and transferId are required"));
        return findOne(tenantId, "id = ?", transferId.value());
    }

    @Override
    public CompletionStage<Optional<HubTransfer>> findByReference(TenantId tenantId, String reference) {
        if (tenantId == null || reference == null || reference.isBlank())
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and reference are required"));
        return findOne(tenantId, "normalized_reference = ?", reference.trim().toUpperCase(Locale.ROOT));
    }

    private CompletionStage<Optional<HubTransfer>> findOne(TenantId tenantId, String predicate, Object value) {
        String sql = "SELECT * FROM courier_hub_transfers WHERE tenant_id = ? AND " + predicate;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId.value());
            if (value instanceof java.util.UUID uuid) statement.setObject(2, uuid);
            else statement.setString(2, value.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return CompletableFuture.completedFuture(Optional.empty());
                HubTransferId id = HubTransferId.of(row.getObject("id", java.util.UUID.class));
                List<ShipmentId> shipments = new ArrayList<>();
                try (PreparedStatement members = connection.prepareStatement("""
                        SELECT shipment_id FROM courier_hub_transfer_shipments
                        WHERE tenant_id = ? AND transfer_id = ? ORDER BY shipment_id
                        """)) {
                    members.setObject(1, tenantId.value());
                    members.setObject(2, id.value());
                    try (ResultSet rows = members.executeQuery()) {
                        while (rows.next()) shipments.add(ShipmentId.of(rows.getObject("shipment_id", java.util.UUID.class)));
                    }
                }
                Timestamp departed = row.getTimestamp("departed_at");
                Timestamp arrived = row.getTimestamp("arrived_at");
                HubTransfer transfer = HubTransfer.reconstitute(
                        row.getString("reference"), tenantId, id,
                        HubId.of(row.getObject("origin_hub_id", java.util.UUID.class)),
                        HubId.of(row.getObject("destination_hub_id", java.util.UUID.class)),
                        HubTransferStatus.valueOf(row.getString("status")),
                        row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
                        departed == null ? null : departed.toInstant(), arrived == null ? null : arrived.toInstant(), shipments);
                return CompletableFuture.completedFuture(Optional.of(transfer));
            }
        } catch (SQLException | RuntimeException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to load hub transfer", failure));
        }
    }
}
