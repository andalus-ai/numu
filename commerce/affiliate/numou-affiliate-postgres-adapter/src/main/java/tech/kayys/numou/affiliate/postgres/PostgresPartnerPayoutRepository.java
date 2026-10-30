package tech.kayys.numou.affiliate.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.affiliate.domain.PartnerPayout;
import tech.kayys.numou.affiliate.domain.PartnerPayoutStatus;
import tech.kayys.numou.affiliate.spi.PartnerPayoutRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * PostgreSQL implementation of the durable payout-intent repository.
 *
 * Every query is tenant-scoped. Idempotency is enforced by the database unique constraint;
 * mutable updates use a compare-and-set predicate so competing workers cannot silently
 * overwrite one another.
 */
@ApplicationScoped
public class PostgresPartnerPayoutRepository implements PartnerPayoutRepository {
    private static final String COLUMNS = "payout_id, tenant_id, partner_id, commission_entry_id, "
            + "destination_id, currency, amount, idempotency_key, correlation_id, payout_status, "
            + "attempts, provider_reference, last_error, created_at, updated_at";

    private final DataSource dataSource;

    @Inject
    public PostgresPartnerPayoutRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletionStage<PartnerPayout> saveOrGet(PartnerPayout proposed) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO affiliate_partner_payout (" + COLUMNS + ") "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING")) {
            bind(insert, proposed);
            int inserted = insert.executeUpdate();
            Optional<PartnerPayout> existing = findByIdempotencyKey(connection,
                    proposed.tenantId(), proposed.idempotencyKey());
            if (existing.isEmpty()) {
                throw new IllegalStateException("Payout insert was not persisted and idempotency key was not found");
            }
            PartnerPayout stored = existing.get();
            if (!sameIntent(stored, proposed)) {
                throw new IllegalStateException("Idempotency key was already used for a different payout intent");
            }
            // If this call inserted the row, the stored row is the proposal. Otherwise return
            // the authoritative existing row, including any later lifecycle state.
            return CompletableFuture.completedFuture(stored);
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(storageFailure("save payout intent", e));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletionStage<Optional<PartnerPayout>> findById(String tenantId, UUID payoutId) {
        try (Connection connection = dataSource.getConnection()) {
            return CompletableFuture.completedFuture(findById(connection, tenantId, payoutId));
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(storageFailure("find payout by id", e));
        }
    }

    @Override
    public CompletionStage<Optional<PartnerPayout>> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        try (Connection connection = dataSource.getConnection()) {
            return CompletableFuture.completedFuture(findByIdempotencyKey(connection, tenantId, idempotencyKey));
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(storageFailure("find payout by idempotency key", e));
        }
    }

    @Override
    public CompletionStage<PartnerPayout> update(String tenantId, UUID payoutId, PartnerPayout replacement) {
        if (!tenantId.equals(replacement.tenantId()) || !payoutId.equals(replacement.id())) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("tenant and payout id must match the replacement"));
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PartnerPayout> currentResult = findById(connection, tenantId, payoutId);
                if (currentResult.isEmpty()) {
                    throw new IllegalStateException("Payout not found for tenant");
                }
                PartnerPayout current = currentResult.get();
                if (!sameIntent(current, replacement)) {
                    throw new IllegalArgumentException("Payout intent fields are immutable");
                }
                if (!validTransition(current.status(), replacement.status())) {
                    throw new IllegalStateException("Illegal payout transition: "
                            + current.status() + " -> " + replacement.status());
                }
                if (replacement.attempts() < current.attempts()) {
                    throw new IllegalArgumentException("Payout attempt count cannot decrease");
                }
                if (!replacement.updatedAt().isAfter(current.updatedAt())) {
                    throw new IllegalArgumentException("Payout update timestamp must advance");
                }

                String sql = "UPDATE affiliate_partner_payout SET payout_status = ?, attempts = ?, "
                        + "provider_reference = ?, last_error = ?, updated_at = ?, version = version + 1 "
                        + "WHERE tenant_id = ? AND payout_id = ? AND payout_status = ? "
                        + "AND updated_at = ?";
                int changed;
                try (PreparedStatement update = connection.prepareStatement(sql)) {
                    update.setString(1, replacement.status().name());
                    update.setInt(2, replacement.attempts());
                    update.setString(3, replacement.providerReference());
                    update.setString(4, replacement.lastError());
                    update.setTimestamp(5, Timestamp.from(replacement.updatedAt()));
                    update.setString(6, tenantId);
                    update.setObject(7, payoutId);
                    update.setString(8, current.status().name());
                    update.setTimestamp(9, Timestamp.from(current.updatedAt()));
                    changed = update.executeUpdate();
                }
                if (changed != 1) {
                    throw new IllegalStateException("Stale payout update; reload before retrying");
                }
                AffiliateEventWriter.append(connection, tenantId, "PARTNER_PAYOUT", payoutId,
                        "PartnerPayout" + replacement.status().name(), "SYSTEM", replacement.correlationId(),
                        "payout:" + payoutId + ":" + replacement.updatedAt(),
                        AffiliateEventWriter.json(replacement.status().name(), -1), replacement.updatedAt());
                connection.commit();
                return CompletableFuture.completedFuture(replacement);
            } catch (RuntimeException | SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                if (e instanceof SQLException sqlException) {
                    throw storageFailure("update payout intent", sqlException);
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return CompletableFuture.failedFuture(storageFailure("update payout intent", e));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Optional<PartnerPayout> findById(Connection connection, String tenantId, UUID payoutId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM affiliate_partner_payout WHERE tenant_id = ? AND payout_id = ?")) {
            statement.setString(1, tenantId);
            statement.setObject(2, payoutId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private Optional<PartnerPayout> findByIdempotencyKey(Connection connection, String tenantId, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM affiliate_partner_payout "
                        + "WHERE tenant_id = ? AND idempotency_key = ?")) {
            statement.setString(1, tenantId);
            statement.setString(2, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static void bind(PreparedStatement statement, PartnerPayout payout) throws SQLException {
        statement.setObject(1, payout.id());
        statement.setString(2, payout.tenantId());
        statement.setObject(3, payout.partnerId());
        statement.setObject(4, payout.commissionEntryId());
        statement.setString(5, payout.destinationId());
        statement.setString(6, payout.currency());
        statement.setBigDecimal(7, payout.amount());
        statement.setString(8, payout.idempotencyKey());
        statement.setString(9, payout.correlationId());
        statement.setString(10, payout.status().name());
        statement.setInt(11, payout.attempts());
        statement.setString(12, payout.providerReference());
        statement.setString(13, payout.lastError());
        statement.setTimestamp(14, Timestamp.from(payout.createdAt()));
        statement.setTimestamp(15, Timestamp.from(payout.updatedAt()));
    }

    private static PartnerPayout map(ResultSet rs) throws SQLException {
        return new PartnerPayout(
                rs.getObject("payout_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("partner_id", UUID.class),
                rs.getObject("commission_entry_id", UUID.class),
                rs.getString("destination_id"),
                rs.getString("currency").trim(),
                rs.getBigDecimal("amount"),
                rs.getString("idempotency_key"),
                rs.getString("correlation_id"),
                PartnerPayoutStatus.valueOf(rs.getString("payout_status")),
                rs.getInt("attempts"),
                rs.getString("provider_reference"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static boolean validTransition(PartnerPayoutStatus current, PartnerPayoutStatus next) {
        return switch (current) {
            case REQUESTED -> next == PartnerPayoutStatus.SUBMITTED || next == PartnerPayoutStatus.PAID
                    || next == PartnerPayoutStatus.HELD || next == PartnerPayoutStatus.RETRYABLE_FAILURE
                    || next == PartnerPayoutStatus.RECONCILIATION_REQUIRED;
            case SUBMITTED -> next == PartnerPayoutStatus.PAID || next == PartnerPayoutStatus.RETURNED
                    || next == PartnerPayoutStatus.RECONCILIATION_REQUIRED
                    || next == PartnerPayoutStatus.RETRYABLE_FAILURE;
            case RETRYABLE_FAILURE -> next == PartnerPayoutStatus.SUBMITTED || next == PartnerPayoutStatus.HELD
                    || next == PartnerPayoutStatus.RECONCILIATION_REQUIRED;
            case RECONCILIATION_REQUIRED -> next == PartnerPayoutStatus.PAID
                    || next == PartnerPayoutStatus.RETURNED || next == PartnerPayoutStatus.RETRYABLE_FAILURE
                    || next == PartnerPayoutStatus.HELD;
            case PAID, HELD, RETURNED -> false;
        };
    }

    private static boolean sameIntent(PartnerPayout left, PartnerPayout right) {
        return left.id().equals(right.id())
                && left.tenantId().equals(right.tenantId())
                && left.partnerId().equals(right.partnerId())
                && left.commissionEntryId().equals(right.commissionEntryId())
                && left.destinationId().equals(right.destinationId())
                && left.currency().equals(right.currency())
                && left.amount().compareTo(right.amount()) == 0
                && left.idempotencyKey().equals(right.idempotencyKey())
                && left.correlationId().equals(right.correlationId())
                && left.createdAt().equals(right.createdAt());
    }

    private static IllegalStateException storageFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
