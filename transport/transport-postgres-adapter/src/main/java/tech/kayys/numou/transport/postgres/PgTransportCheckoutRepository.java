package tech.kayys.numou.transport.postgres;

import tech.kayys.numou.transport.domain.TransportCheckout;
import tech.kayys.numou.transport.domain.TransportCheckoutStatus;
import tech.kayys.numou.transport.spi.TransportCheckoutConcurrencyException;
import tech.kayys.numou.transport.spi.TransportCheckoutRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for durable checkout state and tenant-scoped idempotency. */
public final class PgTransportCheckoutRepository implements TransportCheckoutRepository {
    private static final String COLUMNS = """
            tenant_id, checkout_id, idempotency_key, request_fingerprint, departure_id, quote_id,
            from_sequence, to_sequence, passenger_count, amount, currency, hold_expires_at,
            hold_id, payment_id, payment_action_uri, refund_id, refund_status, refund_reason, status, version, created_at, updated_at
            """;
    private static final String FIND_BY_ID = "SELECT " + COLUMNS
            + " FROM transport_checkout WHERE tenant_id = ? AND checkout_id = ?";
    private static final String FIND_BY_KEY = "SELECT " + COLUMNS
            + " FROM transport_checkout WHERE tenant_id = ? AND idempotency_key = ?";
    private static final String FIND_BY_PAYMENT = "SELECT " + COLUMNS
            + " FROM transport_checkout WHERE tenant_id = ? AND payment_id = ?";
    private static final String FIND_STALE = "SELECT " + COLUMNS
            + " FROM transport_checkout WHERE updated_at < ?"
            + " AND status IN ('REQUESTED', 'HOLD_CREATED', 'PAYMENT_PENDING', 'PAYMENT_ACTION_REQUIRED')"
            + " ORDER BY updated_at ASC, tenant_id ASC, checkout_id ASC LIMIT ?";
    private static final String FIND_STALE_REFUNDS = "SELECT " + COLUMNS
            + " FROM transport_checkout WHERE updated_at < ? AND status = 'REFUND_PENDING'"
            + " ORDER BY updated_at ASC, tenant_id ASC, checkout_id ASC LIMIT ?";
    private static final String INSERT = """
            INSERT INTO transport_checkout
            (tenant_id, checkout_id, idempotency_key, request_fingerprint, departure_id, quote_id,
             from_sequence, to_sequence, passenger_count, amount, currency, hold_expires_at,
             hold_id, payment_id, payment_action_uri, refund_id, refund_status, refund_reason, status, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE = """
            UPDATE transport_checkout
            SET hold_id = ?, payment_id = ?, payment_action_uri = ?, refund_id = ?, refund_status = ?, refund_reason = ?, status = ?, version = ?, updated_at = ?
            WHERE tenant_id = ? AND checkout_id = ? AND version = ?
            """;

    private final DataSource dataSource;

    public PgTransportCheckoutRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<TransportCheckout> find(String tenantId, UUID checkoutId) {
        Objects.requireNonNull(checkoutId, "checkoutId");
        return queryOne(FIND_BY_ID, requireTenant(tenantId), checkoutId);
    }

    @Override
    public Optional<TransportCheckout> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return queryOne(FIND_BY_KEY, requireTenant(tenantId), required(idempotencyKey, "idempotencyKey"));
    }

    @Override
    public Optional<TransportCheckout> findByPaymentId(String tenantId, String paymentId) {
        return queryOne(FIND_BY_PAYMENT, requireTenant(tenantId), required(paymentId, "paymentId"));
    }

    @Override
    public List<TransportCheckout> findStaleNonTerminal(Instant updatedBefore, int limit) {
        Objects.requireNonNull(updatedBefore, "updatedBefore");
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_STALE)) {
            statement.setTimestamp(1, Timestamp.from(updatedBefore));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<TransportCheckout> result = new ArrayList<>();
                while (rows.next()) result.add(read(rows));
                return List.copyOf(result);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("find stale checkouts", failure);
        }
    }

    @Override
    public List<TransportCheckout> findStaleRefunds(Instant updatedBefore, int limit) {
        Objects.requireNonNull(updatedBefore, "updatedBefore");
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_STALE_REFUNDS)) {
            statement.setTimestamp(1, Timestamp.from(updatedBefore));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<TransportCheckout> result = new ArrayList<>();
                while (rows.next()) result.add(read(rows));
                return List.copyOf(result);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("find stale refund operations", failure);
        }
    }

    @Override
    public void create(TransportCheckout checkout) {
        Objects.requireNonNull(checkout, "checkout");
        if (checkout.version() != 0 || checkout.status() != TransportCheckoutStatus.REQUESTED
                || checkout.paymentId() != null) {
            throw new IllegalArgumentException("new checkout must be an initial REQUESTED snapshot");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            bindAll(statement, checkout);
            if (statement.executeUpdate() != 1) {
                throw new TransportCheckoutConcurrencyException("checkout creation did not insert exactly one row");
            }
        } catch (SQLException failure) {
            if ("23505".equals(failure.getSQLState())) {
                throw new TransportCheckoutConcurrencyException("checkout id or idempotency key already exists", failure);
            }
            throw persistenceFailure("create checkout", failure);
        }
    }

    @Override
    public void save(TransportCheckout checkout, int expectedVersion) {
        Objects.requireNonNull(checkout, "checkout");
        if (expectedVersion < 0 || checkout.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("checkout version must advance exactly once");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setObject(1, checkout.holdId());
            statement.setString(2, checkout.paymentId());
            statement.setString(3, checkout.paymentActionUri());
            statement.setString(4, checkout.refundId());
            statement.setString(5, checkout.refundStatus() == null ? null : checkout.refundStatus().name());
            statement.setString(6, checkout.refundReason());
            statement.setString(7, checkout.status().name());
            statement.setInt(8, checkout.version());
            statement.setTimestamp(9, Timestamp.from(checkout.updatedAt()));
            statement.setString(10, checkout.tenantId());
            statement.setObject(11, checkout.checkoutId());
            statement.setInt(12, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new TransportCheckoutConcurrencyException(
                        "checkout version changed or checkout was not found; reload before retrying");
            }
        } catch (TransportCheckoutConcurrencyException conflict) {
            throw conflict;
        } catch (SQLException failure) {
            throw persistenceFailure("save checkout", failure);
        }
    }

    private Optional<TransportCheckout> queryOne(String sql, String tenantId, Object key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            if (key instanceof UUID id) statement.setObject(2, id);
            else statement.setString(2, (String) key);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw persistenceFailure("read checkout", failure);
        }
    }

    private static TransportCheckout read(ResultSet row) throws SQLException {
        return new TransportCheckout(row.getString("tenant_id"),
                row.getObject("checkout_id", UUID.class),
                row.getString("idempotency_key"),
                row.getString("request_fingerprint"),
                row.getObject("departure_id", UUID.class),
                row.getString("quote_id"),
                row.getInt("from_sequence"),
                row.getInt("to_sequence"),
                row.getInt("passenger_count"),
                row.getBigDecimal("amount"),
                row.getString("currency"),
                row.getTimestamp("hold_expires_at").toInstant(),
                row.getObject("hold_id", UUID.class),
                row.getString("payment_id"),
                row.getString("payment_action_uri"),
                row.getString("refund_id"),
                row.getString("refund_status") == null ? null
                        : tech.kayys.numou.transport.domain.TransportCheckoutRefundStatus.valueOf(row.getString("refund_status")),
                row.getString("refund_reason"),
                TransportCheckoutStatus.valueOf(row.getString("status")),
                row.getInt("version"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private static void bindAll(PreparedStatement statement, TransportCheckout checkout) throws SQLException {
        statement.setString(1, checkout.tenantId());
        statement.setObject(2, checkout.checkoutId());
        statement.setString(3, checkout.idempotencyKey());
        statement.setString(4, checkout.requestFingerprint());
        statement.setObject(5, checkout.departureId());
        statement.setString(6, checkout.quoteId());
        statement.setInt(7, checkout.fromSequence());
        statement.setInt(8, checkout.toSequence());
        statement.setInt(9, checkout.passengerCount());
        statement.setBigDecimal(10, checkout.amount());
        statement.setString(11, checkout.currency());
        statement.setTimestamp(12, Timestamp.from(checkout.holdExpiresAt()));
        statement.setObject(13, checkout.holdId());
        statement.setString(14, checkout.paymentId());
        statement.setString(15, checkout.paymentActionUri());
        statement.setString(16, checkout.refundId());
        statement.setString(17, checkout.refundStatus() == null ? null : checkout.refundStatus().name());
        statement.setString(18, checkout.refundReason());
        statement.setString(19, checkout.status().name());
        statement.setInt(20, checkout.version());
        statement.setTimestamp(21, Timestamp.from(checkout.createdAt()));
        statement.setTimestamp(22, Timestamp.from(checkout.updatedAt()));
    }

    private static String requireTenant(String tenantId) {
        return required(tenantId, "tenantId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException failure) {
        return new IllegalStateException("could not " + operation, failure);
    }
}
