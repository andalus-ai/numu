package tech.kayys.numou.transport.postgres;

import tech.kayys.numou.transport.domain.DepartureInventory;
import tech.kayys.numou.transport.domain.PassengerHold;
import tech.kayys.numou.transport.domain.PassengerHoldStatus;
import tech.kayys.numou.transport.spi.DepartureInventoryRepository;
import tech.kayys.numou.transport.spi.InventoryConcurrencyException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC adapter for PostgreSQL. Inventory version comparison and hold writes share one transaction.
 * A caller losing a version race must reload the aggregate and re-run the domain command.
 */
public final class PgDepartureInventoryRepository implements DepartureInventoryRepository {
    private static final String FIND_INVENTORY = """
            SELECT capacity, stop_count, version
            FROM transport_departure_inventory
            WHERE tenant_id = ? AND departure_id = ?
            """;
    private static final String FIND_HOLDS = """
            SELECT hold_id, idempotency_key, request_fingerprint, from_sequence, to_sequence,
                   passenger_count, expires_at, status, created_at, updated_at
            FROM transport_passenger_hold
            WHERE tenant_id = ? AND departure_id = ?
            ORDER BY created_at, hold_id
            """;
    private static final String INSERT_INVENTORY = """
            INSERT INTO transport_departure_inventory (tenant_id, departure_id, capacity, stop_count, version)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_INVENTORY = """
            UPDATE transport_departure_inventory
            SET version = ?
            WHERE tenant_id = ? AND departure_id = ? AND version = ? AND capacity = ? AND stop_count = ?
            """;
    private static final String UPSERT_HOLD = """
            INSERT INTO transport_passenger_hold AS stored
                (tenant_id, departure_id, hold_id, idempotency_key, request_fingerprint,
                 from_sequence, to_sequence, passenger_count, expires_at, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, departure_id, hold_id) DO UPDATE
            SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
            WHERE stored.idempotency_key = EXCLUDED.idempotency_key
              AND stored.request_fingerprint = EXCLUDED.request_fingerprint
              AND stored.from_sequence = EXCLUDED.from_sequence
              AND stored.to_sequence = EXCLUDED.to_sequence
              AND stored.passenger_count = EXCLUDED.passenger_count
              AND stored.expires_at = EXCLUDED.expires_at
              AND stored.created_at = EXCLUDED.created_at
              AND (
                    (stored.status = EXCLUDED.status AND stored.updated_at = EXCLUDED.updated_at)
                    OR (stored.status = 'HELD' AND EXCLUDED.status IN ('CONFIRMED', 'RELEASED', 'EXPIRED')
                        AND EXCLUDED.updated_at >= stored.updated_at)
                    OR (stored.status = 'CONFIRMED' AND EXCLUDED.status = 'RELEASED'
                        AND EXCLUDED.updated_at >= stored.updated_at)
              )
            """;
    private static final String FIND_DUE_INVENTORIES = """
            SELECT DISTINCT i.tenant_id, i.departure_id
            FROM transport_departure_inventory i
            JOIN transport_passenger_hold h
              ON h.tenant_id = i.tenant_id AND h.departure_id = i.departure_id
            WHERE h.status = 'HELD' AND h.expires_at <= ?
            ORDER BY i.tenant_id, i.departure_id
            LIMIT ?
            """;
    private static final String FIND_HOLD_IDS = """
            SELECT hold_id FROM transport_passenger_hold
            WHERE tenant_id = ? AND departure_id = ?
            """;

    private final DataSource dataSource;

    public PgDepartureInventoryRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<DepartureInventory> find(String tenantId, UUID departureId) {
        String tenant = requireTenant(tenantId);
        Objects.requireNonNull(departureId, "departureId");
        return transaction(Connection.TRANSACTION_REPEATABLE_READ, true, connection -> {
            int capacity;
            int stopCount;
            int version;
            try (PreparedStatement statement = connection.prepareStatement(FIND_INVENTORY)) {
                statement.setString(1, tenant);
                statement.setObject(2, departureId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return Optional.empty();
                    capacity = row.getInt("capacity");
                    stopCount = row.getInt("stop_count");
                    version = Math.toIntExact(row.getLong("version"));
                }
            }
            List<PassengerHold> holds = loadHolds(connection, tenant, departureId);
            return Optional.of(DepartureInventory.reconstitute(
                    tenant, departureId, capacity, stopCount, version, holds));
        });
    }

    @Override
    public List<DepartureInventoryRepository.InventoryKey> findDueInventories(java.time.Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000");
        }
        return transaction(Connection.TRANSACTION_READ_COMMITTED, true, connection -> {
            List<DepartureInventoryRepository.InventoryKey> due = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(FIND_DUE_INVENTORIES)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        due.add(new DepartureInventoryRepository.InventoryKey(
                                rows.getString("tenant_id"), rows.getObject("departure_id", UUID.class)));
                    }
                }
            }
            return List.copyOf(due);
        });
    }

    @Override
    public void create(DepartureInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        if (inventory.version() != 0 || !inventory.holds().isEmpty()) {
            throw new IllegalArgumentException("new inventory must be an empty version-zero snapshot");
        }
        transaction(Connection.TRANSACTION_READ_COMMITTED, false, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_INVENTORY)) {
                statement.setString(1, inventory.tenantId());
                statement.setObject(2, inventory.departureId());
                statement.setInt(3, inventory.capacity());
                statement.setInt(4, inventory.stopCount());
                statement.setLong(5, inventory.version());
                if (statement.executeUpdate() != 1) {
                    throw new InventoryConcurrencyException("inventory creation did not insert exactly one row");
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new InventoryConcurrencyException("inventory already exists for this tenant and departure", e);
                }
                throw e;
            }
            return null;
        });
    }

    @Override
    public void save(DepartureInventory inventory, int expectedVersion) {
        Objects.requireNonNull(inventory, "inventory");
        if (expectedVersion < 0 || inventory.version() <= expectedVersion) {
            throw new IllegalArgumentException("saved inventory version must be greater than expectedVersion");
        }
        transaction(Connection.TRANSACTION_READ_COMMITTED, false, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_INVENTORY)) {
                statement.setLong(1, inventory.version());
                statement.setString(2, inventory.tenantId());
                statement.setObject(3, inventory.departureId());
                statement.setInt(4, expectedVersion);
                statement.setInt(5, inventory.capacity());
                statement.setInt(6, inventory.stopCount());
                if (statement.executeUpdate() != 1) {
                    throw new InventoryConcurrencyException(
                            "inventory version changed or inventory was not found; reload before retrying");
                }
            }
            ensureHistoryRetained(connection, inventory);
            persistHolds(connection, inventory);
            return null;
        });
    }

    private List<PassengerHold> loadHolds(Connection connection, String tenantId, UUID departureId)
            throws SQLException {
        List<PassengerHold> holds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(FIND_HOLDS)) {
            statement.setString(1, tenantId);
            statement.setObject(2, departureId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    holds.add(new PassengerHold(
                            rows.getObject("hold_id", UUID.class),
                            rows.getString("idempotency_key"),
                            rows.getString("request_fingerprint"),
                            rows.getInt("from_sequence"),
                            rows.getInt("to_sequence"),
                            rows.getInt("passenger_count"),
                            rows.getTimestamp("expires_at").toInstant(),
                            PassengerHoldStatus.valueOf(rows.getString("status")),
                            rows.getTimestamp("created_at").toInstant(),
                            rows.getTimestamp("updated_at").toInstant()));
                }
            }
        }
        return List.copyOf(holds);
    }

    private void ensureHistoryRetained(Connection connection, DepartureInventory inventory) throws SQLException {
        java.util.Set<UUID> suppliedIds = inventory.holds().stream()
                .map(PassengerHold::id)
                .collect(java.util.stream.Collectors.toSet());
        try (PreparedStatement statement = connection.prepareStatement(FIND_HOLD_IDS)) {
            statement.setString(1, inventory.tenantId());
            statement.setObject(2, inventory.departureId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID persistedId = rows.getObject("hold_id", UUID.class);
                    if (!suppliedIds.contains(persistedId)) {
                        throw new IllegalArgumentException("inventory snapshot cannot omit persisted hold history");
                    }
                }
            }
        }
    }

    private void persistHolds(Connection connection, DepartureInventory inventory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_HOLD)) {
            for (PassengerHold hold : inventory.holds()) {
                statement.setString(1, inventory.tenantId());
                statement.setObject(2, inventory.departureId());
                statement.setObject(3, hold.id());
                statement.setString(4, hold.idempotencyKey());
                statement.setString(5, hold.requestFingerprint());
                statement.setInt(6, hold.fromSequence());
                statement.setInt(7, hold.toSequence());
                statement.setInt(8, hold.passengerCount());
                statement.setTimestamp(9, Timestamp.from(hold.expiresAt()));
                statement.setString(10, hold.status().name());
                statement.setTimestamp(11, Timestamp.from(hold.createdAt()));
                statement.setTimestamp(12, Timestamp.from(hold.updatedAt()));
                if (statement.executeUpdate() != 1) {
                    throw new InventoryConcurrencyException("persisted hold identity conflicts with the supplied snapshot");
                }
            }
        }
    }

    private <T> T transaction(int isolation, boolean readOnly, SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            int originalIsolation = connection.getTransactionIsolation();
            boolean originalReadOnly = connection.isReadOnly();
            try {
                connection.setTransactionIsolation(isolation);
                connection.setReadOnly(readOnly);
                connection.setAutoCommit(false);
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("transport inventory database operation failed", failure);
            } finally {
                try {
                    connection.setReadOnly(originalReadOnly);
                    connection.setTransactionIsolation(originalIsolation);
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is closed immediately after this block; do not mask the transaction result.
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("unable to access transport inventory database", e);
        }
    }

    private static String requireTenant(String value) {
        Objects.requireNonNull(value, "tenantId");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("tenantId must not be blank");
        if (normalized.length() > 128) throw new IllegalArgumentException("tenantId must not exceed 128 characters");
        return normalized;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
