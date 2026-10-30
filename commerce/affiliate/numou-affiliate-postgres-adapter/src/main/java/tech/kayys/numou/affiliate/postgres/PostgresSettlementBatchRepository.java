package tech.kayys.numou.affiliate.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.affiliate.domain.PartnerPayoutStatus;
import tech.kayys.numou.affiliate.domain.SettlementBatch;
import tech.kayys.numou.affiliate.domain.SettlementBatchItem;
import tech.kayys.numou.affiliate.domain.SettlementBatchStatus;
import tech.kayys.numou.affiliate.spi.SettlementBatchRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * PostgreSQL settlement batch repository.
 *
 * Batch, item, and payout reservation rows are written in one database transaction.
 * Payout rows are locked in stable UUID order before validating and reserving them.
 */
@ApplicationScoped
public class PostgresSettlementBatchRepository implements SettlementBatchRepository {
    private final DataSource dataSource;

    @Inject
    public PostgresSettlementBatchRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletionStage<SettlementBatch> saveOrGet(SettlementBatch proposed) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String insertSql = "INSERT INTO affiliate_settlement_batch "
                        + "(batch_id, tenant_id, currency, total_amount, idempotency_key, batch_status, "
                        + "created_by, approved_by, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING";
                int inserted;
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setObject(1, proposed.id());
                    insert.setString(2, proposed.tenantId());
                    insert.setString(3, proposed.currency());
                    insert.setBigDecimal(4, proposed.totalAmount());
                    insert.setString(5, proposed.idempotencyKey());
                    insert.setString(6, proposed.status().name());
                    insert.setString(7, proposed.createdBy());
                    insert.setString(8, proposed.approvedBy());
                    insert.setTimestamp(9, Timestamp.from(proposed.createdAt()));
                    insert.setTimestamp(10, Timestamp.from(proposed.updatedAt()));
                    insert.setLong(11, proposed.version());
                    inserted = insert.executeUpdate();
                }

                if (inserted == 0) {
                    Optional<SettlementBatch> stored = findByIdempotencyKey(connection,
                            proposed.tenantId(), proposed.idempotencyKey());
                    if (stored.isEmpty()) {
                        throw new IllegalStateException("Batch insert conflicted but existing batch was not found");
                    }
                    if (!sameRequest(stored.get(), proposed)) {
                        throw new IllegalStateException(
                                "Idempotency key was already used for a different settlement batch");
                    }
                    connection.commit();
                    return CompletableFuture.completedFuture(stored.get());
                }

                List<SettlementBatchItem> orderedItems = proposed.items().stream()
                        .sorted(Comparator.comparing(item -> item.payoutId().toString())).toList();
                for (SettlementBatchItem item : orderedItems) {
                    validateAndLockPayout(connection, proposed, item);
                }
                insertItemsAndReservations(connection, proposed, orderedItems);
                AffiliateEventWriter.append(connection, proposed.tenantId(), "SETTLEMENT_BATCH", proposed.id(),
                        "SettlementBatchCreated", proposed.createdBy(), null,
                        "settlement-batch:" + proposed.id() + ":created",
                        AffiliateEventWriter.json(proposed.status().name(), proposed.version()), proposed.createdAt());
                connection.commit();
                return CompletableFuture.completedFuture(proposed);
            } catch (RuntimeException | SQLException e) {
                rollback(connection, e);
                if (e instanceof SQLException sqlException) {
                    throw storageFailure("save settlement batch", sqlException);
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(storageFailure("save settlement batch", e));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletionStage<Optional<SettlementBatch>> findById(String tenantId, UUID batchId) {
        try (Connection connection = dataSource.getConnection()) {
            return CompletableFuture.completedFuture(findById(connection, tenantId, batchId));
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(storageFailure("find settlement batch", e));
        }
    }

    @Override
    public CompletionStage<SettlementBatch> update(
            String tenantId, UUID batchId, long expectedVersion, SettlementBatch replacement) {
        if (!tenantId.equals(replacement.tenantId()) || !batchId.equals(replacement.id())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("tenant and batch id must match the replacement"));
        }
        if (replacement.version() != expectedVersion + 1) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("replacement version must be expectedVersion + 1"));
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<SettlementBatch> currentResult = findById(connection, tenantId, batchId);
                if (currentResult.isEmpty()) throw new IllegalStateException("Batch not found for tenant");
                SettlementBatch current = currentResult.get();
                if (current.version() != expectedVersion) {
                    throw new IllegalStateException("Stale settlement batch version; reload before retrying");
                }
                if (!sameIntent(current, replacement)) {
                    throw new IllegalArgumentException("Settlement batch intent and items are immutable");
                }
                if (!validTransition(current.status(), replacement.status())) {
                    throw new IllegalStateException("Illegal settlement batch transition: "
                            + current.status() + " -> " + replacement.status());
                }
                if (!replacement.updatedAt().isAfter(current.updatedAt())) {
                    throw new IllegalArgumentException("Batch update timestamp must advance");
                }

                String sql = "UPDATE affiliate_settlement_batch SET batch_status = ?, approved_by = ?, "
                        + "updated_at = ?, version = version + 1 WHERE tenant_id = ? AND batch_id = ? AND version = ?";
                int changed;
                try (PreparedStatement update = connection.prepareStatement(sql)) {
                    update.setString(1, replacement.status().name());
                    update.setString(2, replacement.approvedBy());
                    update.setTimestamp(3, Timestamp.from(replacement.updatedAt()));
                    update.setString(4, tenantId);
                    update.setObject(5, batchId);
                    update.setLong(6, expectedVersion);
                    changed = update.executeUpdate();
                }
                if (changed != 1) throw new IllegalStateException("Stale settlement batch update");

                if (replacement.status() == SettlementBatchStatus.CANCELLED
                        || replacement.status() == SettlementBatchStatus.COMPLETED) {
                    try (PreparedStatement release = connection.prepareStatement(
                            "DELETE FROM affiliate_settlement_payout_reservation "
                                    + "WHERE tenant_id = ? AND batch_id = ?")) {
                        release.setString(1, tenantId);
                        release.setObject(2, batchId);
                        release.executeUpdate();
                    }
                }
                connection.commit();
                return CompletableFuture.completedFuture(replacement);
            } catch (RuntimeException | SQLException e) {
                rollback(connection, e);
                if (e instanceof SQLException sqlException) {
                    throw storageFailure("update settlement batch", sqlException);
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(storageFailure("update settlement batch", e));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void validateAndLockPayout(Connection connection, SettlementBatch batch, SettlementBatchItem item)
            throws SQLException {
        String sql = "SELECT partner_id, currency, amount, payout_status FROM affiliate_partner_payout "
                + "WHERE tenant_id = ? AND payout_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, batch.tenantId());
            statement.setObject(2, item.payoutId());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Payout not found for tenant: " + item.payoutId());
                UUID partnerId = rs.getObject("partner_id", UUID.class);
                String currency = rs.getString("currency").trim();
                if (!partnerId.equals(item.partnerId())
                        || !currency.equalsIgnoreCase(item.currency())
                        || rs.getBigDecimal("amount").compareTo(item.amount()) != 0) {
                    throw new IllegalArgumentException("Settlement item does not match payout snapshot: " + item.payoutId());
                }
                if (!currency.equalsIgnoreCase(batch.currency())) {
                    throw new IllegalArgumentException("Payout currency does not match batch currency");
                }
                PartnerPayoutStatus status = PartnerPayoutStatus.valueOf(rs.getString("payout_status"));
                if (status != PartnerPayoutStatus.REQUESTED) {
                    throw new IllegalStateException("Only requested payouts can be reserved: " + item.payoutId());
                }
            }
        }
    }

    private void insertItemsAndReservations(
            Connection connection, SettlementBatch batch, List<SettlementBatchItem> items) throws SQLException {
        String itemSql = "INSERT INTO affiliate_settlement_batch_item "
                + "(tenant_id, batch_id, payout_id, partner_id, amount, currency) VALUES (?, ?, ?, ?, ?, ?)";
        String reservationSql = "INSERT INTO affiliate_settlement_payout_reservation "
                + "(tenant_id, payout_id, batch_id) VALUES (?, ?, ?)";
        try (PreparedStatement itemInsert = connection.prepareStatement(itemSql);
             PreparedStatement reservationInsert = connection.prepareStatement(reservationSql)) {
            for (SettlementBatchItem item : items) {
                itemInsert.setString(1, batch.tenantId());
                itemInsert.setObject(2, batch.id());
                itemInsert.setObject(3, item.payoutId());
                itemInsert.setObject(4, item.partnerId());
                itemInsert.setBigDecimal(5, item.amount());
                itemInsert.setString(6, item.currency());
                itemInsert.addBatch();

                reservationInsert.setString(1, batch.tenantId());
                reservationInsert.setObject(2, item.payoutId());
                reservationInsert.setObject(3, batch.id());
                reservationInsert.addBatch();
            }
            itemInsert.executeBatch();
            // The tenant+payout primary key is the final race-safe guard against double reservation.
            reservationInsert.executeBatch();
        }
    }

    private Optional<SettlementBatch> findById(Connection connection, String tenantId, UUID batchId)
            throws SQLException {
        String sql = "SELECT batch_id, tenant_id, currency, total_amount, idempotency_key, batch_status, "
                + "created_by, approved_by, created_at, updated_at, version FROM affiliate_settlement_batch "
                + "WHERE tenant_id = ? AND batch_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setObject(2, batchId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(connection, rs)) : Optional.empty();
            }
        }
    }

    private Optional<SettlementBatch> findByIdempotencyKey(Connection connection, String tenantId, String key)
            throws SQLException {
        String sql = "SELECT batch_id, tenant_id, currency, total_amount, idempotency_key, batch_status, "
                + "created_by, approved_by, created_at, updated_at, version FROM affiliate_settlement_batch "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setString(2, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(connection, rs)) : Optional.empty();
            }
        }
    }

    private SettlementBatch map(Connection connection, ResultSet rs) throws SQLException {
        UUID batchId = rs.getObject("batch_id", UUID.class);
        String tenantId = rs.getString("tenant_id");
        List<SettlementBatchItem> items = new ArrayList<>();
        String itemSql = "SELECT payout_id, partner_id, amount, currency FROM affiliate_settlement_batch_item "
                + "WHERE tenant_id = ? AND batch_id = ? ORDER BY payout_id";
        try (PreparedStatement itemQuery = connection.prepareStatement(itemSql)) {
            itemQuery.setString(1, tenantId);
            itemQuery.setObject(2, batchId);
            try (ResultSet itemRows = itemQuery.executeQuery()) {
                while (itemRows.next()) {
                    items.add(new SettlementBatchItem(
                            itemRows.getObject("payout_id", UUID.class),
                            itemRows.getObject("partner_id", UUID.class),
                            itemRows.getBigDecimal("amount"),
                            itemRows.getString("currency").trim()));
                }
            }
        }
        return new SettlementBatch(batchId, tenantId, rs.getString("currency").trim(), items,
                rs.getBigDecimal("total_amount"), rs.getString("idempotency_key"),
                SettlementBatchStatus.valueOf(rs.getString("batch_status")), rs.getString("created_by"),
                rs.getString("approved_by"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    private static boolean sameRequest(SettlementBatch left, SettlementBatch right) {
        return left.tenantId().equals(right.tenantId())
                && left.currency().equals(right.currency())
                && left.totalAmount().compareTo(right.totalAmount()) == 0
                && left.idempotencyKey().equals(right.idempotencyKey())
                && left.createdBy().equals(right.createdBy())
                && sameItems(left.items(), right.items());
    }

    private static boolean sameIntent(SettlementBatch left, SettlementBatch right) {
        return left.id().equals(right.id())
                && left.tenantId().equals(right.tenantId())
                && left.currency().equals(right.currency())
                && left.totalAmount().compareTo(right.totalAmount()) == 0
                && left.idempotencyKey().equals(right.idempotencyKey())
                && left.createdBy().equals(right.createdBy())
                && left.createdAt().equals(right.createdAt())
                && sameItems(left.items(), right.items());
    }

    private static boolean sameItems(List<SettlementBatchItem> left, List<SettlementBatchItem> right) {
        if (left.size() != right.size()) return false;
        List<SettlementBatchItem> leftItems = left.stream()
                .sorted(Comparator.comparing(item -> item.payoutId().toString())).toList();
        List<SettlementBatchItem> rightItems = right.stream()
                .sorted(Comparator.comparing(item -> item.payoutId().toString())).toList();
        for (int i = 0; i < leftItems.size(); i++) {
            SettlementBatchItem a = leftItems.get(i);
            SettlementBatchItem b = rightItems.get(i);
            if (!a.payoutId().equals(b.payoutId()) || !a.partnerId().equals(b.partnerId())
                    || a.amount().compareTo(b.amount()) != 0 || !a.currency().equals(b.currency())) return false;
        }
        return true;
    }

    private static boolean validTransition(SettlementBatchStatus current, SettlementBatchStatus next) {
        return switch (current) {
            case READY_FOR_APPROVAL -> next == SettlementBatchStatus.APPROVED
                    || next == SettlementBatchStatus.CANCELLED;
            case APPROVED -> next == SettlementBatchStatus.SUBMITTING;
            case SUBMITTING -> next == SettlementBatchStatus.COMPLETED
                    || next == SettlementBatchStatus.PARTIALLY_COMPLETED
                    || next == SettlementBatchStatus.RECONCILIATION_REQUIRED;
            case PARTIALLY_COMPLETED -> next == SettlementBatchStatus.RECONCILIATION_REQUIRED;
            case COMPLETED, CANCELLED, RECONCILIATION_REQUIRED -> false;
        };
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static IllegalStateException storageFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
