package tech.kayys.numou.pos.adapter.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.application.port.out.CheckoutRepository;
import tech.kayys.numou.pos.domain.CheckoutSession;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL durable checkout state; every lookup and mutation is tenant-scoped. */
@ApplicationScoped
public class PostgresCheckoutRepository implements CheckoutRepository {
    private final DataSource dataSource;

    @Inject
    public PostgresCheckoutRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public Optional<CheckoutSession> find(String tenantId, UUID checkoutId) {
        String sql = "SELECT * FROM pos_checkout_session WHERE tenant_id = ? AND checkout_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId); ps.setObject(2, checkoutId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(read(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw new CheckoutStorageException("Unable to load checkout", e); }
    }


    @Override
    public java.util.List<CheckoutSession> findRecoverable(String tenantId, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        String sql = "SELECT * FROM pos_checkout_session WHERE tenant_id = ? AND status IN "
                + "('PAYMENT_PENDING', 'PAYMENT_UNKNOWN', 'PAID', 'COMPENSATION_REQUIRED') "
                + "ORDER BY updated_at ASC, checkout_id ASC LIMIT ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<CheckoutSession> sessions = new java.util.ArrayList<>();
                while (rs.next()) sessions.add(read(rs));
                return java.util.List.copyOf(sessions);
            }
        } catch (SQLException e) { throw new CheckoutStorageException("Unable to list recoverable checkouts", e); }
    }

    @Override
    @Transactional
    public CheckoutSession create(CheckoutSession s) {
        String sql = "INSERT INTO pos_checkout_session (tenant_id, checkout_id, sale_id, amount, currency, status, "
                + "reservation_reference, payment_id, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, s); ps.executeUpdate(); return s;
        } catch (SQLException e) { throw new CheckoutStorageException("Unable to create checkout; identifier may already exist", e); }
    }

    @Override
    @Transactional
    public CheckoutSession update(CheckoutSession s, long expectedVersion) {
        String sql = "UPDATE pos_checkout_session SET sale_id=?, amount=?, currency=?, status=?, reservation_reference=?, "
                + "payment_id=?, version=version+1, updated_at=? WHERE tenant_id=? AND checkout_id=? AND version=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, s.saleId()); ps.setBigDecimal(2, s.amount().amount());
            ps.setString(3, s.amount().currency().code()); ps.setString(4, s.status().name());
            ps.setString(5, s.reservationReference()); ps.setString(6, s.paymentId());
            ps.setTimestamp(7, Timestamp.from(s.updatedAt())); ps.setString(8, s.tenantId());
            ps.setObject(9, s.id()); ps.setLong(10, expectedVersion);
            if (ps.executeUpdate() != 1) throw new OptimisticCheckoutConflictException(s.tenantId(), s.id(), expectedVersion);
            return new CheckoutSession(s.id(), s.tenantId(), s.saleId(), s.amount(), s.status(),
                    s.reservationReference(), s.paymentId(), s.createdAt(), s.updatedAt(), expectedVersion + 1);
        } catch (SQLException e) { throw new CheckoutStorageException("Unable to update checkout", e); }
    }

    private static void bind(PreparedStatement ps, CheckoutSession s) throws SQLException {
        ps.setString(1, s.tenantId()); ps.setObject(2, s.id()); ps.setObject(3, s.saleId());
        ps.setBigDecimal(4, s.amount().amount()); ps.setString(5, s.amount().currency().code());
        ps.setString(6, s.status().name()); ps.setString(7, s.reservationReference());
        ps.setString(8, s.paymentId()); ps.setLong(9, s.version());
        ps.setTimestamp(10, Timestamp.from(s.createdAt())); ps.setTimestamp(11, Timestamp.from(s.updatedAt()));
    }

    private static CheckoutSession read(ResultSet rs) throws SQLException {
        return new CheckoutSession(rs.getObject("checkout_id", UUID.class), rs.getString("tenant_id"),
                rs.getObject("sale_id", UUID.class),
                Money.of(rs.getBigDecimal("amount"), Currency.of(rs.getString("currency"))),
                CheckoutSession.Status.valueOf(rs.getString("status")), rs.getString("reservation_reference"),
                rs.getString("payment_id"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    public static final class OptimisticCheckoutConflictException extends RuntimeException {
        public OptimisticCheckoutConflictException(String tenant, UUID id, long version) {
            super("Checkout version conflict for tenant " + tenant + ", checkout " + id + ", expected " + version);
        }
    }
    public static final class CheckoutStorageException extends RuntimeException {
        public CheckoutStorageException(String message, Throwable cause) { super(message, cause); }
    }
}
