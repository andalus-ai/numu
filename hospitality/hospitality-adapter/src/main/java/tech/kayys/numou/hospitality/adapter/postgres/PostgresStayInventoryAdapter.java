package tech.kayys.numou.hospitality.adapter.postgres;

import io.smallrye.mutiny.Uni;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import tech.kayys.numou.hospitality.domain.StayBooking;
import tech.kayys.numou.hospitality.domain.StayBookingStatus;
import tech.kayys.numou.hospitality.spi.AtomicStayHoldPort;
import tech.kayys.numou.hospitality.spi.StayBookingRepository;
import tech.kayys.numou.hospitality.spi.StayInventoryProvisioningPort;
import tech.kayys.numou.hospitality.spi.StayBookingLifecyclePort;

/**
 * PostgreSQL implementation of multi-night inventory holds.
 *
 * <p>All requested unit/date rows are locked in ascending date order in one transaction.
 * Competing requests for any overlapping night therefore serialize on the same inventory
 * row. A transaction-scoped advisory lock serializes retries of the same tenant/customer
 * idempotency key even when the competing requests ask for different dates.</p>
 *
 * <p>Blocking JDBC work is dispatched to the caller-supplied executor. The host application
 * must configure a bounded worker pool and run the supplied schema migration before use.</p>
 */
public final class PostgresStayInventoryAdapter implements AtomicStayHoldPort, StayBookingRepository, StayInventoryProvisioningPort, StayBookingLifecyclePort {
    private final DataSource dataSource;
    private final Executor jdbcExecutor;

    public PostgresStayInventoryAdapter(DataSource dataSource, Executor jdbcExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbcExecutor = Objects.requireNonNull(jdbcExecutor, "jdbcExecutor");
    }

    @Override
    public Uni<Void> provision(String tenantId, UUID listingId, UUID unitId,
                               LocalDate checkIn, LocalDate checkOut, int capacity) {
        if (tenantId == null || tenantId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("tenantId must not be blank"));
        }
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        if (!checkOut.isAfter(checkIn)) {
            return Uni.createFrom().failure(new IllegalArgumentException("checkOut must be after checkIn"));
        }
        if (capacity < 1) {
            return Uni.createFrom().failure(new IllegalArgumentException("capacity must be positive"));
        }
        return async(() -> {
            List<LocalDate> dates = nights(checkIn, checkOut);
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    String sql = "INSERT INTO hospitality_inventory_night (tenant_id,listing_id,unit_id,stay_date,capacity) " +
                            "VALUES (?,?,?,?,?) ON CONFLICT (tenant_id,unit_id,stay_date) DO UPDATE SET capacity=EXCLUDED.capacity " +
                            "WHERE hospitality_inventory_night.listing_id=EXCLUDED.listing_id AND EXCLUDED.capacity >= (" +
                            "SELECT COUNT(*) FROM hospitality_stay_booking_night n JOIN hospitality_stay_booking b " +
                            "ON b.tenant_id=n.tenant_id AND b.booking_id=n.booking_id " +
                            "WHERE n.tenant_id=EXCLUDED.tenant_id AND n.unit_id=EXCLUDED.unit_id AND n.stay_date=EXCLUDED.stay_date " +
                            "AND (b.booking_status='CONFIRMED' OR (b.booking_status='HELD' AND b.hold_expires_at>CURRENT_TIMESTAMP)))";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        for (LocalDate date : dates) {
                            statement.setString(1, tenantId.trim());
                            statement.setObject(2, listingId);
                            statement.setObject(3, unitId);
                            statement.setDate(4, Date.valueOf(date));
                            statement.setInt(5, capacity);
                            if (statement.executeUpdate() != 1) {
                                throw new IllegalArgumentException(
                                        "Capacity cannot be reduced below active bookings or listing ownership differs for " + date);
                            }
                        }
                    }
                    connection.commit();
                    return null;
                } catch (RuntimeException | SQLException exception) {
                    rollback(connection, exception);
                    if (exception instanceof RuntimeException runtimeException) throw runtimeException;
                    throw persistenceFailure((SQLException) exception);
                } finally {
                    try { connection.setAutoCommit(true); } catch (SQLException ignored) { /* closing connection */ }
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<StayBooking> createHoldAtomically(StayBooking booking, Instant holdExpiresAt) {
        Objects.requireNonNull(booking, "booking");
        Objects.requireNonNull(holdExpiresAt, "holdExpiresAt");
        if (!holdExpiresAt.equals(booking.holdExpiresAt())) {
            return Uni.createFrom().failure(new IllegalArgumentException("hold expiry must match booking snapshot"));
        }
        StayBooking persistedPrecisionBooking = normalizePostgresTimestamps(booking);
        return async(() -> createHoldTransactionally(persistedPrecisionBooking));
    }

    @Override
    public Uni<Optional<StayBooking>> findByIdempotencyKey(String tenantId, String customerId, String idempotencyKey) {
        return async(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return findByKey(connection, tenantId, customerId, idempotencyKey);
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    @Override
    public Uni<Optional<StayBooking>> findById(String tenantId, UUID bookingId) {
        return async(() -> {
            String sql = "SELECT * FROM hospitality_stay_booking WHERE tenant_id = ? AND booking_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setObject(2, bookingId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(readBooking(rs)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }


    @Override
    public Uni<Optional<StayBooking>> confirm(String tenantId, UUID bookingId, Instant now) {
        requireLifecycleArguments(tenantId, bookingId, now);
        return async(() -> transitionBooking(tenantId, bookingId, now, LifecycleAction.CONFIRM));
    }

    @Override
    public Uni<Optional<StayBooking>> cancel(String tenantId, UUID bookingId, Instant now) {
        requireLifecycleArguments(tenantId, bookingId, now);
        return async(() -> transitionBooking(tenantId, bookingId, now, LifecycleAction.CANCEL));
    }

    @Override
    public Uni<Integer> expireDue(Instant now, int batchSize) {
        Objects.requireNonNull(now, "now");
        if (batchSize < 1 || batchSize > 1000) {
            return Uni.createFrom().failure(new IllegalArgumentException("batchSize must be between 1 and 1000"));
        }
        return async(() -> {
            String sql = "WITH due AS (SELECT tenant_id, booking_id FROM hospitality_stay_booking " +
                    "WHERE booking_status='HELD' AND hold_expires_at <= ? " +
                    "ORDER BY hold_expires_at, tenant_id, booking_id LIMIT ? FOR UPDATE SKIP LOCKED) " +
                    "UPDATE hospitality_stay_booking b SET booking_status='EXPIRED' FROM due " +
                    "WHERE b.tenant_id=due.tenant_id AND b.booking_id=due.booking_id AND b.booking_status='HELD'";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setInt(2, batchSize);
                return statement.executeUpdate();
            } catch (SQLException exception) {
                throw persistenceFailure(exception);
            }
        });
    }

    private Optional<StayBooking> transitionBooking(String tenantId, UUID bookingId, Instant now,
                                                    LifecycleAction action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String select = "SELECT * FROM hospitality_stay_booking WHERE tenant_id=? AND booking_id=? FOR UPDATE";
                StayBooking booking;
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setString(1, tenantId.trim());
                    statement.setObject(2, bookingId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            connection.commit();
                            return Optional.empty();
                        }
                        booking = readBooking(rs);
                    }
                }

                StayBookingStatus current = booking.status();
                StayBookingStatus target = current;
                if (action == LifecycleAction.CONFIRM) {
                    if (current == StayBookingStatus.HELD) {
                        target = now.isBefore(booking.holdExpiresAt())
                                ? StayBookingStatus.CONFIRMED : StayBookingStatus.EXPIRED;
                    } else if (current != StayBookingStatus.CONFIRMED) {
                        throw new InvalidStayBookingTransitionException(current, action.name());
                    }
                } else {
                    if (current == StayBookingStatus.CANCELLED) {
                        target = StayBookingStatus.CANCELLED;
                    } else if (current == StayBookingStatus.HELD) {
                        target = now.isBefore(booking.holdExpiresAt())
                                ? StayBookingStatus.CANCELLED : StayBookingStatus.EXPIRED;
                    } else if (current == StayBookingStatus.CONFIRMED) {
                        target = StayBookingStatus.CANCELLED;
                    } else {
                        throw new InvalidStayBookingTransitionException(current, action.name());
                    }
                }

                if (target != current) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE hospitality_stay_booking SET booking_status=? WHERE tenant_id=? AND booking_id=? AND booking_status=?")) {
                        statement.setString(1, target.name());
                        statement.setString(2, tenantId.trim());
                        statement.setObject(3, bookingId);
                        statement.setString(4, current.name());
                        if (statement.executeUpdate() != 1) {
                            throw new IllegalStateException("Booking lifecycle changed concurrently; retry the command");
                        }
                    }
                    booking = StayBooking.restore(booking.id(), booking.tenantId(), booking.customerId(),
                            booking.listingId(), booking.unitId(), booking.checkIn(), booking.checkOut(),
                            booking.guestCount(), booking.quoteId(), booking.currency(), booking.idempotencyKey(),
                            booking.createdAt(), booking.holdExpiresAt(), target);
                }
                connection.commit();
                return Optional.of(booking);
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                if (exception instanceof RuntimeException runtimeException) throw runtimeException;
                throw persistenceFailure((SQLException) exception);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) { /* closing connection */ }
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static void requireLifecycleArguments(String tenantId, UUID bookingId, Instant now) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(now, "now");
    }

    private enum LifecycleAction { CONFIRM, CANCEL }

    public static final class InvalidStayBookingTransitionException extends RuntimeException {
        public InvalidStayBookingTransitionException(StayBookingStatus current, String action) {
            super("Cannot " + action.toLowerCase(java.util.Locale.ROOT) + " booking in status " + current);
        }
    }

    /** PostgreSQL timestamp columns retain microsecond precision and round higher precision values.
     * Normalize before both persistence and returning the newly-created aggregate so the first
     * response and every idempotent replay expose the same immutable timestamp snapshot.
     */
    private static StayBooking normalizePostgresTimestamps(StayBooking booking) {
        return StayBooking.restore(booking.id(), booking.tenantId(), booking.customerId(),
                booking.listingId(), booking.unitId(), booking.checkIn(), booking.checkOut(),
                booking.guestCount(), booking.quoteId(), booking.currency(), booking.idempotencyKey(),
                booking.createdAt().truncatedTo(ChronoUnit.MICROS),
                booking.holdExpiresAt().truncatedTo(ChronoUnit.MICROS), booking.status());
    }

    private StayBooking createHoldTransactionally(StayBooking proposed) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockIdempotencyKey(connection, proposed);
                Optional<StayBooking> existing = findByKey(connection, proposed.tenantId(),
                        proposed.customerId(), proposed.idempotencyKey());
                if (existing.isPresent()) {
                    StayBooking prior = existing.get();
                    if (!sameRequest(prior, proposed)) {
                        throw new IdempotencyConflictException();
                    }
                    connection.commit();
                    return prior;
                }

                List<LocalDate> nights = nights(proposed.checkIn(), proposed.checkOut());
                lockInventoryNights(connection, proposed, nights);
                verifyCapacity(connection, proposed, nights);
                insertBooking(connection, proposed);
                insertBookingNights(connection, proposed, nights);
                connection.commit();
                return proposed;
            } catch (RuntimeException | SQLException exception) {
                rollback(connection, exception);
                if (isUniqueViolation(exception)) {
                    // A unique-key race can only be resolved after the failed transaction ends.
                    return findAfterRace(proposed);
                }
                if (exception instanceof RuntimeException runtimeException) throw runtimeException;
                throw persistenceFailure((SQLException) exception);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) { /* closing connection */ }
            }
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private StayBooking findAfterRace(StayBooking proposed) {
        try (Connection connection = dataSource.getConnection()) {
            Optional<StayBooking> existing = findByKey(connection, proposed.tenantId(),
                    proposed.customerId(), proposed.idempotencyKey());
            if (existing.isPresent() && sameRequest(existing.get(), proposed)) return existing.get();
            if (existing.isPresent()) throw new IdempotencyConflictException();
            throw new IllegalStateException("Unique constraint failed but no idempotent booking was found");
        } catch (SQLException exception) {
            throw persistenceFailure(exception);
        }
    }

    private static void lockIdempotencyKey(Connection connection, StayBooking booking) throws SQLException {
        String key = booking.tenantId() + "\u001f" + booking.customerId() + "\u001f" + booking.idempotencyKey();
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, key);
            statement.execute();
        }
    }

    private static void lockInventoryNights(Connection connection, StayBooking booking, List<LocalDate> nights)
            throws SQLException {
        String sql = "SELECT listing_id, stay_date FROM hospitality_inventory_night " +
                "WHERE tenant_id = ? AND unit_id = ? AND stay_date >= ? AND stay_date < ? " +
                "ORDER BY stay_date FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, booking.tenantId());
            statement.setObject(2, booking.unitId());
            statement.setDate(3, Date.valueOf(booking.checkIn()));
            statement.setDate(4, Date.valueOf(booking.checkOut()));
            try (ResultSet rs = statement.executeQuery()) {
                int found = 0;
                while (rs.next()) {
                    UUID listingId = rs.getObject("listing_id", UUID.class);
                    if (!booking.listingId().equals(listingId)) {
                        throw new IllegalStateException("Inventory listing does not match booking listing");
                    }
                    found++;
                }
                if (found != nights.size()) {
                    throw new StayInventoryNotProvisionedException("Inventory must be provisioned for every requested night");
                }
            }
        }
    }

    private static void verifyCapacity(Connection connection, StayBooking booking, List<LocalDate> nights)
            throws SQLException {
        String sql = "SELECT i.capacity, " +
                "(SELECT COUNT(*) FROM hospitality_stay_booking_night n " +
                " JOIN hospitality_stay_booking b ON b.tenant_id=n.tenant_id AND b.booking_id=n.booking_id " +
                " WHERE n.tenant_id=i.tenant_id AND n.unit_id=i.unit_id AND n.stay_date=i.stay_date " +
                " AND (b.booking_status='CONFIRMED' OR (b.booking_status='HELD' AND b.hold_expires_at > ?))) AS occupied " +
                "FROM hospitality_inventory_night i WHERE i.tenant_id=? AND i.unit_id=? AND i.stay_date=? FOR UPDATE";
        Instant now = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LocalDate night : nights) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setString(2, booking.tenantId());
                statement.setObject(3, booking.unitId());
                statement.setDate(4, Date.valueOf(night));
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) throw new StayInventoryNotProvisionedException("Inventory row missing for " + night);
                    if (rs.getInt("occupied") >= rs.getInt("capacity")) {
                        throw new StayInventoryUnavailableException("No inventory available for " + night);
                    }
                }
            }
        }
    }

    private static void insertBooking(Connection connection, StayBooking booking) throws SQLException {
        String sql = "INSERT INTO hospitality_stay_booking " +
                "(tenant_id,booking_id,customer_id,idempotency_key,listing_id,unit_id,check_in,check_out,guest_count,quote_id,currency,booking_status,created_at,hold_expires_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,'HELD',?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, booking.tenantId());
            statement.setObject(2, booking.id());
            statement.setString(3, booking.customerId());
            statement.setString(4, booking.idempotencyKey());
            statement.setObject(5, booking.listingId());
            statement.setObject(6, booking.unitId());
            statement.setDate(7, Date.valueOf(booking.checkIn()));
            statement.setDate(8, Date.valueOf(booking.checkOut()));
            statement.setInt(9, booking.guestCount());
            statement.setObject(10, booking.quoteId());
            statement.setString(11, booking.currency());
            statement.setTimestamp(12, Timestamp.from(booking.createdAt()));
            statement.setTimestamp(13, Timestamp.from(booking.holdExpiresAt()));
            statement.executeUpdate();
        }
    }

    private static void insertBookingNights(Connection connection, StayBooking booking, List<LocalDate> nights)
            throws SQLException {
        String sql = "INSERT INTO hospitality_stay_booking_night (tenant_id,booking_id,unit_id,stay_date) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LocalDate night : nights) {
                statement.setString(1, booking.tenantId());
                statement.setObject(2, booking.id());
                statement.setObject(3, booking.unitId());
                statement.setDate(4, Date.valueOf(night));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Optional<StayBooking> findByKey(Connection connection, String tenantId, String customerId,
                                                    String idempotencyKey) throws SQLException {
        String sql = "SELECT * FROM hospitality_stay_booking WHERE tenant_id=? AND customer_id=? AND idempotency_key=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setString(2, customerId);
            statement.setString(3, idempotencyKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(readBooking(rs)) : Optional.empty();
            }
        }
    }

    private static StayBooking readBooking(ResultSet rs) throws SQLException {
        return StayBooking.restore(
                rs.getObject("booking_id", UUID.class), rs.getString("tenant_id"), rs.getString("customer_id"),
                rs.getObject("listing_id", UUID.class), rs.getObject("unit_id", UUID.class),
                rs.getDate("check_in").toLocalDate(), rs.getDate("check_out").toLocalDate(),
                rs.getInt("guest_count"), rs.getObject("quote_id", UUID.class), rs.getString("currency").trim(),
                rs.getString("idempotency_key"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("hold_expires_at").toInstant(),
                StayBookingStatus.valueOf(rs.getString("booking_status")));
    }

    private static boolean sameRequest(StayBooking a, StayBooking b) {
        return a.tenantId().equals(b.tenantId()) && a.customerId().equals(b.customerId())
                && a.listingId().equals(b.listingId()) && a.unitId().equals(b.unitId())
                && a.checkIn().equals(b.checkIn()) && a.checkOut().equals(b.checkOut())
                && a.guestCount() == b.guestCount() && a.currency().equalsIgnoreCase(b.currency());
    }

    private static List<LocalDate> nights(LocalDate checkIn, LocalDate checkOut) {
        long count = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (count < 1 || count > 366) throw new IllegalArgumentException("Stay length must be between 1 and 366 nights");
        List<LocalDate> nights = new ArrayList<>((int) count);
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) nights.add(date);
        return nights;
    }

    private static boolean isUniqueViolation(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    private static void rollback(Connection connection, Throwable failure) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }

    private static RuntimeException persistenceFailure(SQLException exception) {
        return new IllegalStateException("Hospitality inventory persistence operation failed", exception);
    }

    private <T> Uni<T> async(SqlSupplier<T> supplier) {
        CompletionStage<T> stage = CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException("Hospitality inventory operation failed", exception); }
        }, jdbcExecutor);
        return Uni.createFrom().completionStage(stage);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> { T get() throws Exception; }

    public static final class StayInventoryUnavailableException extends RuntimeException {
        public StayInventoryUnavailableException(String message) { super(message); }
    }

    public static final class StayInventoryNotProvisionedException extends RuntimeException {
        public StayInventoryNotProvisionedException(String message) { super(message); }
    }

    public static final class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException() { super("Idempotency key already belongs to a different booking request"); }
    }
}
