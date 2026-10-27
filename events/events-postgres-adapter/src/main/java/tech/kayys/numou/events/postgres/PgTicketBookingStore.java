package tech.kayys.numou.events.postgres;

import tech.kayys.numou.events.spi.TicketBookingException;
import tech.kayys.numou.events.spi.TicketBookingException.Code;
import tech.kayys.numou.events.spi.TicketBookingStore;
import tech.kayys.numou.events.spi.TicketBookingStore.BookingSnapshot;
import tech.kayys.numou.events.spi.TicketBookingStore.HoldCommand;
import tech.kayys.numou.events.spi.TicketBookingStore.Inventory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC/PostgreSQL adapter. Inventory row locks serialize capacity decisions across application instances.
 * This adapter expects the host's migration runner to apply db/migration resources before use.
 */
public final class PgTicketBookingStore implements TicketBookingStore {
    private final DataSource dataSource;

    public PgTicketBookingStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void createInventory(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        String sql = """
                INSERT INTO event_ticket_inventory
                    (tenant_id, event_id, product_id, currency, unit_price, capacity, max_per_order)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, event_id, product_id) DO NOTHING
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, inventory.tenantId());
            s.setString(2, inventory.eventId());
            s.setString(3, inventory.productId());
            s.setString(4, inventory.currency());
            s.setBigDecimal(5, inventory.unitPrice());
            s.setInt(6, inventory.capacity());
            s.setInt(7, inventory.maxPerOrder());
            if (s.executeUpdate() == 0) {
                throw new TicketBookingException(Code.INVALID_BOOKING_STATE,
                        "ticket inventory already exists; changing published capacity requires an explicit audited command");
            }
        } catch (SQLException e) {
            throw persistence("Unable to create ticket inventory", e);
        }
    }

    @Override
    public BookingSnapshot placeHold(HoldCommand command) {
        Objects.requireNonNull(command, "command");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!reserveIdempotency(c, command)) {
                    BookingSnapshot previous = findByIdempotency(c, command.tenantId(), command.idempotencyKey());
                    if (!previous.requestFingerprint().equals(command.requestFingerprint())) {
                        throw new TicketBookingException(Code.IDEMPOTENCY_CONFLICT,
                                "idempotency key was already used with a different booking request");
                    }
                    c.commit();
                    return previous;
                }

                InventoryRow inventory = lockInventory(c, command.tenantId(), command.eventId(), command.productId());
                if (inventory == null) {
                    throw new TicketBookingException(Code.INVENTORY_NOT_FOUND, "ticket product inventory was not found");
                }
                if (!inventory.currency.equals(command.currency())
                        || inventory.unitPrice.compareTo(command.unitPrice()) != 0) {
                    throw new TicketBookingException(Code.INVALID_BOOKING_STATE,
                            "requested ticket price or currency does not match the published inventory price");
                }
                if (command.quantity() > inventory.maxPerOrder) {
                    throw new TicketBookingException(Code.INSUFFICIENT_INVENTORY, "quantity exceeds per-order limit");
                }

                expireProductHolds(c, command.tenantId(), command.eventId(), command.productId(), command.createdAt());
                int activeHeld = activeHeldQuantity(c, command.tenantId(), command.eventId(),
                        command.productId(), command.createdAt());
                if (inventory.capacity - inventory.soldQuantity - activeHeld < command.quantity()) {
                    throw new TicketBookingException(Code.INSUFFICIENT_INVENTORY, "insufficient ticket inventory");
                }

                String insert = """
                        INSERT INTO event_ticket_booking
                            (tenant_id, booking_id, event_id, product_id, actor_id, correlation_id,
                             idempotency_key, request_fingerprint, sales_channel, quantity, unit_price,
                             total_amount, currency, status, created_at, expires_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'HELD', ?, ?, ?)
                        """;
                BigDecimal total = command.unitPrice().multiply(BigDecimal.valueOf(command.quantity()));
                try (PreparedStatement s = c.prepareStatement(insert)) {
                    s.setString(1, command.tenantId());
                    s.setObject(2, command.bookingId());
                    s.setString(3, command.eventId());
                    s.setString(4, command.productId());
                    s.setString(5, command.actorId());
                    s.setString(6, command.correlationId());
                    s.setString(7, command.idempotencyKey());
                    s.setString(8, command.requestFingerprint());
                    s.setString(9, command.salesChannel());
                    s.setInt(10, command.quantity());
                    s.setBigDecimal(11, command.unitPrice());
                    s.setBigDecimal(12, total);
                    s.setString(13, command.currency());
                    s.setTimestamp(14, Timestamp.from(command.createdAt()));
                    s.setTimestamp(15, Timestamp.from(command.expiresAt()));
                    s.setTimestamp(16, Timestamp.from(command.createdAt()));
                    s.executeUpdate();
                }
                c.commit();
                return find(command.tenantId(), command.bookingId());
            } catch (RuntimeException | SQLException e) {
                rollback(c, e);
                if (e instanceof TicketBookingException t) throw t;
                if (e instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new TicketBookingException(Code.IDEMPOTENCY_CONFLICT,
                            "booking idempotency key conflicts with an existing booking", e);
                }
                if (e instanceof SQLException sql) throw persistence("Unable to create ticket booking hold", sql);
                throw (RuntimeException) e;
            }
        } catch (SQLException e) {
            throw persistence("Unable to open booking transaction", e);
        }
    }

    @Override
    public BookingSnapshot confirm(String tenantId, UUID bookingId, String paymentReference, Instant now) {
        String tenant = required(tenantId, "tenantId");
        Objects.requireNonNull(bookingId, "bookingId");
        String reference = required(paymentReference, "paymentReference");
        Objects.requireNonNull(now, "now");
        if (reference.length() > 240) throw new IllegalArgumentException("paymentReference is too long");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BookingSnapshot initial = find(c, tenant, bookingId, true);
                lockInventory(c, tenant, initial.eventId(), initial.productId());
                BookingSnapshot current = find(c, tenant, bookingId, true);
                if ("CONFIRMED".equals(current.status())) {
                    if (!reference.equals(current.paymentReference())) {
                        throw new TicketBookingException(Code.INVALID_PAYMENT_REFERENCE,
                                "booking is already confirmed against a different payment reference");
                    }
                    c.commit();
                    return current;
                }
                if (!"HELD".equals(current.status())) {
                    throw new TicketBookingException(Code.INVALID_BOOKING_STATE,
                            "only an active hold can be confirmed; current status is " + current.status());
                }
                if (!now.isBefore(current.expiresAt())) {
                    updateStatus(c, tenant, bookingId, "EXPIRED", now, null, null);
                    c.commit();
                    throw new TicketBookingException(Code.INVALID_BOOKING_STATE,
                            "booking hold expired before payment confirmation");
                }
                String updateBooking = """
                        UPDATE event_ticket_booking SET status = 'CONFIRMED', payment_reference = ?, updated_at = ?
                        WHERE tenant_id = ? AND booking_id = ? AND status = 'HELD'
                        """;
                try (PreparedStatement s = c.prepareStatement(updateBooking)) {
                    s.setString(1, reference);
                    s.setTimestamp(2, Timestamp.from(now));
                    s.setString(3, tenant);
                    s.setObject(4, bookingId);
                    if (s.executeUpdate() != 1) throw new TicketBookingException(Code.INVALID_BOOKING_STATE,
                            "booking changed while being confirmed");
                }
                try (PreparedStatement s = c.prepareStatement("""
                        UPDATE event_ticket_inventory SET sold_quantity = sold_quantity + ?
                        WHERE tenant_id = ? AND event_id = ? AND product_id = ? AND sold_quantity + ? <= capacity
                        """)) {
                    s.setInt(1, current.quantity());
                    s.setString(2, tenant);
                    s.setString(3, current.eventId());
                    s.setString(4, current.productId());
                    s.setInt(5, current.quantity());
                    if (s.executeUpdate() != 1) throw new TicketBookingException(Code.INSUFFICIENT_INVENTORY,
                            "inventory capacity changed before confirmation");
                }
                c.commit();
                return find(tenant, bookingId);
            } catch (RuntimeException | SQLException e) {
                if (!c.isClosed() && c.getAutoCommit() == false) rollback(c, e);
                if (e instanceof TicketBookingException t) throw t;
                if (e instanceof SQLException sql) throw persistence("Unable to confirm ticket booking", sql);
                throw (RuntimeException) e;
            }
        } catch (SQLException e) {
            throw persistence("Unable to open confirmation transaction", e);
        }
    }

    @Override
    public BookingSnapshot release(String tenantId, UUID bookingId, String reason, Instant now) {
        String tenant = required(tenantId, "tenantId");
        String releaseReason = required(reason, "reason");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(now, "now");
        if (releaseReason.length() > 500) throw new IllegalArgumentException("reason is too long");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BookingSnapshot current = find(c, tenant, bookingId, true);
                lockInventory(c, tenant, current.eventId(), current.productId());
                current = find(c, tenant, bookingId, true);
                if ("RELEASED".equals(current.status()) || "EXPIRED".equals(current.status())) {
                    c.commit();
                    return current;
                }
                if (!"HELD".equals(current.status())) {
                    throw new TicketBookingException(Code.INVALID_BOOKING_STATE,
                            "only a held booking can be released; refund workflow is separate");
                }
                updateStatus(c, tenant, bookingId, "RELEASED", now, null, releaseReason);
                c.commit();
                return find(tenant, bookingId);
            } catch (RuntimeException | SQLException e) {
                rollback(c, e);
                if (e instanceof TicketBookingException t) throw t;
                if (e instanceof SQLException sql) throw persistence("Unable to release ticket booking", sql);
                throw (RuntimeException) e;
            }
        } catch (SQLException e) {
            throw persistence("Unable to open release transaction", e);
        }
    }

    @Override
    public int expireDueHolds(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 1 || limit > 10_000) throw new IllegalArgumentException("limit must be between 1 and 10000");
        String sql = """
                WITH due AS (
                    SELECT tenant_id, booking_id FROM event_ticket_booking
                    WHERE status = 'HELD' AND expires_at <= ?
                    ORDER BY expires_at, tenant_id, booking_id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE event_ticket_booking b SET status = 'EXPIRED', updated_at = ?
                FROM due WHERE b.tenant_id = due.tenant_id AND b.booking_id = due.booking_id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setTimestamp(1, Timestamp.from(now));
            s.setInt(2, limit);
            s.setTimestamp(3, Timestamp.from(now));
            return s.executeUpdate();
        } catch (SQLException e) {
            throw persistence("Unable to expire due ticket holds", e);
        }
    }

    private boolean reserveIdempotency(Connection c, HoldCommand command) throws SQLException {
        String sql = """
                INSERT INTO event_ticket_idempotency
                    (tenant_id, idempotency_key, request_fingerprint, booking_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, command.tenantId());
            s.setString(2, command.idempotencyKey());
            s.setString(3, command.requestFingerprint());
            s.setObject(4, command.bookingId());
            s.setTimestamp(5, Timestamp.from(command.createdAt()));
            return s.executeUpdate() == 1;
        }
    }

    private BookingSnapshot findByIdempotency(Connection c, String tenant, String key) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                SELECT request_fingerprint, booking_id FROM event_ticket_idempotency
                WHERE tenant_id = ? AND idempotency_key = ?
                """)) {
            s.setString(1, tenant);
            s.setString(2, key);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new TicketBookingException(Code.PERSISTENCE_FAILURE,
                        "idempotency reservation disappeared during transaction");
                String fingerprint = r.getString(1);
                UUID bookingId = r.getObject(2, UUID.class);
                BookingSnapshot booking = find(c, tenant, bookingId, false);
                if (!fingerprint.equals(booking.requestFingerprint())) {
                    throw new TicketBookingException(Code.PERSISTENCE_FAILURE,
                            "idempotency record and booking fingerprint disagree");
                }
                return booking;
            }
        }
    }

    private InventoryRow lockInventory(Connection c, String tenant, String event, String product) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                SELECT currency, unit_price, capacity, sold_quantity, max_per_order
                FROM event_ticket_inventory WHERE tenant_id = ? AND event_id = ? AND product_id = ?
                FOR UPDATE
                """)) {
            s.setString(1, tenant); s.setString(2, event); s.setString(3, product);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return new InventoryRow(r.getString(1), r.getBigDecimal(2), r.getInt(3), r.getInt(4), r.getInt(5));
            }
        }
    }

    private void expireProductHolds(Connection c, String tenant, String event, String product, Instant now) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE event_ticket_booking SET status = 'EXPIRED', updated_at = ?
                WHERE tenant_id = ? AND event_id = ? AND product_id = ? AND status = 'HELD' AND expires_at <= ?
                """)) {
            s.setTimestamp(1, Timestamp.from(now)); s.setString(2, tenant); s.setString(3, event);
            s.setString(4, product); s.setTimestamp(5, Timestamp.from(now)); s.executeUpdate();
        }
    }

    private int activeHeldQuantity(Connection c, String tenant, String event, String product, Instant now) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                SELECT COALESCE(SUM(quantity), 0) FROM event_ticket_booking
                WHERE tenant_id = ? AND event_id = ? AND product_id = ? AND status = 'HELD' AND expires_at > ?
                """)) {
            s.setString(1, tenant); s.setString(2, event); s.setString(3, product); s.setTimestamp(4, Timestamp.from(now));
            try (ResultSet r = s.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }

    private void updateStatus(Connection c, String tenant, UUID bookingId, String status,
                              Instant now, String paymentReference, String reason) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                UPDATE event_ticket_booking SET status = ?, updated_at = ?,
                    payment_reference = COALESCE(?, payment_reference), release_reason = COALESCE(?, release_reason)
                WHERE tenant_id = ? AND booking_id = ?
                """)) {
            s.setString(1, status); s.setTimestamp(2, Timestamp.from(now)); s.setString(3, paymentReference);
            s.setString(4, reason); s.setString(5, tenant); s.setObject(6, bookingId); s.executeUpdate();
        }
    }

    private BookingSnapshot find(String tenant, UUID bookingId) {
        try (Connection c = dataSource.getConnection()) { return find(c, tenant, bookingId, false); }
        catch (SQLException e) { throw persistence("Unable to read ticket booking", e); }
    }

    private BookingSnapshot find(Connection c, String tenant, UUID bookingId, boolean forUpdate) throws SQLException {
        String sql = """
                SELECT booking_id, tenant_id, event_id, product_id, actor_id, correlation_id, idempotency_key,
                       request_fingerprint, sales_channel, quantity, unit_price, total_amount, currency,
                       status, created_at, expires_at, payment_reference, release_reason
                FROM event_ticket_booking WHERE tenant_id = ? AND booking_id = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, tenant); s.setObject(2, bookingId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new TicketBookingException(Code.BOOKING_NOT_FOUND, "ticket booking was not found");
                return snapshot(r);
            }
        }
    }

    private static BookingSnapshot snapshot(ResultSet r) throws SQLException {
        return new BookingSnapshot(r.getObject("booking_id", UUID.class), r.getString("tenant_id"),
                r.getString("event_id"), r.getString("product_id"), r.getString("actor_id"),
                r.getString("correlation_id"), r.getString("idempotency_key"), r.getString("request_fingerprint"),
                r.getString("sales_channel"), r.getInt("quantity"), r.getBigDecimal("unit_price"),
                r.getBigDecimal("total_amount"), r.getString("currency"), r.getString("status"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("expires_at").toInstant(),
                r.getString("payment_reference"), r.getString("release_reason"));
    }

    private static void rollback(Connection c, Throwable original) {
        try { c.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }

    private static TicketBookingException persistence(String message, SQLException cause) {
        return new TicketBookingException(Code.PERSISTENCE_FAILURE, message, cause);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private record InventoryRow(String currency, BigDecimal unitPrice, int capacity, int soldQuantity, int maxPerOrder) { }
}
