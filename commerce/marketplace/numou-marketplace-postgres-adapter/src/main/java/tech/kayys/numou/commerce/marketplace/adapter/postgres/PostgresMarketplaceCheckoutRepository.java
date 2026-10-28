package tech.kayys.numou.commerce.marketplace.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.commerce.marketplace.application.checkout.MarketplaceCheckout;
import tech.kayys.numou.commerce.marketplace.application.checkout.MarketplaceCheckoutRepository;
import tech.kayys.numou.commerce.marketplace.application.checkout.MarketplaceCheckoutStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for durable checkout checkpoints and tenant-scoped idempotency. */
@ApplicationScoped
public class PostgresMarketplaceCheckoutRepository implements MarketplaceCheckoutRepository {
    private final DataSource dataSource;

    @Inject
    public PostgresMarketplaceCheckoutRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public MarketplaceCheckout createOrGet(MarketplaceCheckout proposed) {
        Objects.requireNonNull(proposed, "proposed checkout is required");
        return inTransaction(connection -> {
            int inserted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO marketplace_checkout
                        (checkout_id, tenant_id, order_id, idempotency_key, request_fingerprint,
                         checkout_status, payment_id, failure_code, failure_detail, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                    """)) {
                statement.setObject(1, proposed.id());
                statement.setString(2, proposed.tenantId());
                statement.setObject(3, proposed.orderId());
                statement.setString(4, proposed.idempotencyKey());
                statement.setString(5, proposed.requestFingerprint());
                statement.setString(6, proposed.status().name());
                statement.setString(7, proposed.paymentId());
                statement.setString(8, proposed.failureCode());
                statement.setString(9, proposed.failureDetail());
                statement.setLong(10, proposed.version());
                statement.setTimestamp(11, Timestamp.from(proposed.createdAt()));
                statement.setTimestamp(12, Timestamp.from(proposed.updatedAt()));
                inserted = statement.executeUpdate();
            }
            if (inserted == 1) return proposed;

            MarketplaceCheckout existing = loadByKey(connection, proposed.tenantId(), proposed.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Checkout idempotency claim was not found"));
            if (!existing.requestFingerprint().equals(proposed.requestFingerprint())
                    || !existing.orderId().equals(proposed.orderId())) {
                throw new IllegalStateException("Checkout idempotency key was reused for a different request");
            }
            return existing;
        });
    }

    @Override
    public Optional<MarketplaceCheckout> findByTenantAndId(String tenantId, UUID checkoutId) {
        requireTenant(tenantId);
        Objects.requireNonNull(checkoutId, "checkoutId is required");
        try (Connection connection = dataSource.getConnection()) {
            return load(connection, tenantId, checkoutId);
        } catch (SQLException e) {
            throw failure("Could not load marketplace checkout", e);
        }
    }

    @Override
    public Optional<MarketplaceCheckout> findByTenantAndKey(String tenantId, String idempotencyKey) {
        requireTenant(tenantId);
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("idempotencyKey is required");
        try (Connection connection = dataSource.getConnection()) {
            return loadByKey(connection, tenantId, idempotencyKey);
        } catch (SQLException e) {
            throw failure("Could not load marketplace checkout by idempotency key", e);
        }
    }

    @Override
    public boolean saveIfVersion(String tenantId, UUID checkoutId, long expectedVersion,
                                 MarketplaceCheckout replacement) {
        requireTenant(tenantId);
        Objects.requireNonNull(checkoutId, "checkoutId is required");
        Objects.requireNonNull(replacement, "replacement is required");
        if (!tenantId.equals(replacement.tenantId()) || !checkoutId.equals(replacement.id())) {
            throw new IllegalArgumentException("Replacement checkout identity must match tenant and checkout id");
        }
        if (replacement.version() != expectedVersion) {
            throw new IllegalArgumentException("Replacement checkout must carry the expected version");
        }
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE marketplace_checkout
                    SET checkout_status = ?, payment_id = ?, failure_code = ?, failure_detail = ?,
                        updated_at = ?, version = version + 1
                    WHERE tenant_id = ? AND checkout_id = ? AND version = ?
                    """)) {
                statement.setString(1, replacement.status().name());
                statement.setString(2, replacement.paymentId());
                statement.setString(3, replacement.failureCode());
                statement.setString(4, replacement.failureDetail());
                statement.setTimestamp(5, Timestamp.from(replacement.updatedAt()));
                statement.setString(6, tenantId);
                statement.setObject(7, checkoutId);
                statement.setLong(8, expectedVersion);
                if (statement.executeUpdate() != 1) return false;
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO marketplace_checkout_reservation
                        (tenant_id, checkout_id, line_id, reservation_id)
                    VALUES (?, ?, ?, ?) ON CONFLICT (tenant_id, checkout_id, line_id) DO NOTHING
                    """)) {
                for (var entry : replacement.reservationIds().entrySet()) {
                    insert.setString(1, tenantId);
                    insert.setObject(2, checkoutId);
                    insert.setString(3, entry.getKey());
                    insert.setString(4, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            Map<String, String> persisted = loadReservations(connection, tenantId, checkoutId);
            if (!persisted.equals(replacement.reservationIds())) {
                throw new IllegalStateException("Checkout reservation checkpoints are immutable and must match persisted values");
            }
            return true;
        });
    }

    private Optional<MarketplaceCheckout> loadByKey(Connection connection, String tenantId, String key)
            throws SQLException {
        UUID id;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT checkout_id FROM marketplace_checkout WHERE tenant_id = ? AND idempotency_key = ?
                """)) {
            statement.setString(1, tenantId);
            statement.setString(2, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                id = result.getObject("checkout_id", UUID.class);
            }
        }
        return load(connection, tenantId, id);
    }

    private Optional<MarketplaceCheckout> load(Connection connection, String tenantId, UUID id) throws SQLException {
        UUID orderId;
        String key;
        String fingerprint;
        MarketplaceCheckoutStatus status;
        String paymentId;
        String failureCode;
        String failureDetail;
        long version;
        java.time.Instant createdAt;
        java.time.Instant updatedAt;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT order_id, idempotency_key, request_fingerprint, checkout_status, payment_id,
                       failure_code, failure_detail, version, created_at, updated_at
                FROM marketplace_checkout WHERE tenant_id = ? AND checkout_id = ?
                """)) {
            statement.setString(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                orderId = result.getObject("order_id", UUID.class);
                key = result.getString("idempotency_key");
                fingerprint = result.getString("request_fingerprint").trim();
                status = MarketplaceCheckoutStatus.valueOf(result.getString("checkout_status"));
                paymentId = result.getString("payment_id");
                failureCode = result.getString("failure_code");
                failureDetail = result.getString("failure_detail");
                version = result.getLong("version");
                createdAt = result.getTimestamp("created_at").toInstant();
                updatedAt = result.getTimestamp("updated_at").toInstant();
            }
        }
        Map<String, String> reservations = loadReservations(connection, tenantId, id);
        return Optional.of(new MarketplaceCheckout(id, tenantId, orderId, key, fingerprint, status,
                reservations, paymentId, failureCode, failureDetail, version, createdAt, updatedAt));
    }

    private static Map<String, String> loadReservations(Connection connection, String tenantId, UUID checkoutId)
            throws SQLException {
        Map<String, String> reservations = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT line_id, reservation_id FROM marketplace_checkout_reservation
                WHERE tenant_id = ? AND checkout_id = ? ORDER BY line_id
                """)) {
            statement.setString(1, tenantId);
            statement.setObject(2, checkoutId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) reservations.put(result.getString("line_id"), result.getString("reservation_id"));
            }
        }
        return Map.copyOf(reservations);
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T value = work.apply(connection);
                connection.commit();
                return value;
            } catch (RuntimeException | SQLException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw failure("Marketplace checkout persistence failed", (SQLException) e);
            } finally {
                try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) { }
            }
        } catch (SQLException e) {
            throw failure("Marketplace checkout database connection failed", e);
        }
    }

    private static IllegalStateException failure(String message, SQLException cause) {
        return new IllegalStateException(message, cause);
    }

    @FunctionalInterface
    private interface SqlWork<T> { T apply(Connection connection) throws SQLException; }
}
