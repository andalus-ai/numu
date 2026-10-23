package tech.kayys.numou.warehouse.persistence.jdbc;

import tech.kayys.numou.warehouse.application.*;
import tech.kayys.numou.warehouse.domain.*;

import javax.sql.DataSource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.Base64;

/**
 * PostgreSQL JDBC adapter for the warehouse aggregate, command receipt and outbox.
 * A single JDBC transaction commits all three records. Snapshot encoding is deliberately
 * local to this adapter so the domain/application modules remain persistence-neutral.
 */
public final class JdbcWarehouseWorkRepository implements WarehouseWorkRepository {
    private final DataSource dataSource;

    public JdbcWarehouseWorkRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<WarehouseWorkOrder> find(String tenantId, String workOrderId) {
        require(tenantId, "tenantId"); require(workOrderId, "workOrderId");
        String sql = "SELECT snapshot_payload FROM warehouse_work_order WHERE tenant_id=? AND work_order_id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, tenantId.trim()); p.setString(2, workOrderId.trim());
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(decodeOrder(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException e) { throw failure("find warehouse work order", e); }
    }

    @Override
    public Optional<WarehouseCommandReceipt> findReceipt(String tenantId, String commandKey) {
        require(tenantId, "tenantId"); require(commandKey, "commandKey");
        String sql = "SELECT fingerprint,result_payload,committed_at FROM warehouse_command_receipt WHERE tenant_id=? AND command_key=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, tenantId.trim()); p.setString(2, commandKey.trim());
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new WarehouseCommandReceipt(tenantId.trim(), commandKey.trim(),
                        rs.getString(1), decodeOrder(rs.getString(2)), rs.getTimestamp(3).toInstant()));
            }
        } catch (SQLException e) { throw failure("find warehouse command receipt", e); }
    }

    @Override
    public WarehouseCommitResult commit(String tenantId, long expectedVersion, WarehouseWorkOrder candidate,
                                        String commandKey, String fingerprint, List<WarehouseOutboxEvent> events) {
        require(tenantId, "tenantId"); require(commandKey, "commandKey"); require(fingerprint, "fingerprint");
        Objects.requireNonNull(candidate, "candidate"); Objects.requireNonNull(events, "events");
        String tenant = tenantId.trim(), key = commandKey.trim();
        if (!tenant.equals(candidate.tenantId())) throw new IllegalArgumentException("Tenant mismatch");
        if (expectedVersion < 0 || candidate.version() != expectedVersion + 1)
            throw new IllegalArgumentException("Candidate version must be expectedVersion + 1");
        for (WarehouseOutboxEvent event : events) {
            if (!tenant.equals(event.tenantId()) || !candidate.id().equals(event.aggregateId())
                    || event.aggregateVersion() != candidate.version())
                throw new IllegalArgumentException("Outbox event identity/version mismatch");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                WarehouseCommandReceipt existing = receipt(c, tenant, key);
                if (existing != null) {
                    if (!existing.fingerprint().equals(fingerprint))
                        throw new IllegalStateException("Command key reused with different payload");
                    c.rollback();
                    return new WarehouseCommitResult(existing.result(), true);
                }

                String payload = encodeOrder(candidate);
                if (expectedVersion == 0) {
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO warehouse_work_order(tenant_id,work_order_id,aggregate_version,snapshot_payload,updated_at) VALUES(?,?,?,?,?)")) {
                        p.setString(1, tenant); p.setString(2, candidate.id()); p.setLong(3, candidate.version());
                        p.setString(4, payload); p.setTimestamp(5, Timestamp.from(candidate.updatedAt()));
                        p.executeUpdate();
                    }
                } else {
                    try (PreparedStatement p = c.prepareStatement(
                            "UPDATE warehouse_work_order SET aggregate_version=?,snapshot_payload=?,updated_at=? WHERE tenant_id=? AND work_order_id=? AND aggregate_version=?")) {
                        p.setLong(1, candidate.version()); p.setString(2, payload);
                        p.setTimestamp(3, Timestamp.from(candidate.updatedAt())); p.setString(4, tenant);
                        p.setString(5, candidate.id()); p.setLong(6, expectedVersion);
                        if (p.executeUpdate() != 1) throw new OptimisticLockException("Warehouse work order version changed");
                    }
                }

                Instant committedAt = Instant.now();
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO warehouse_command_receipt(tenant_id,command_key,fingerprint,work_order_id,result_version,result_payload,committed_at) VALUES(?,?,?,?,?,?,?)")) {
                    p.setString(1, tenant); p.setString(2, key); p.setString(3, fingerprint);
                    p.setString(4, candidate.id()); p.setLong(5, candidate.version()); p.setString(6, payload);
                    p.setTimestamp(7, Timestamp.from(committedAt)); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO warehouse_outbox_event(event_id,tenant_id,aggregate_id,aggregate_version,event_type,payload,occurred_at) VALUES(?,?,?,?,?,?,?)")) {
                    for (WarehouseOutboxEvent event : events) {
                        p.setString(1, event.eventId()); p.setString(2, tenant); p.setString(3, event.aggregateId());
                        p.setLong(4, event.aggregateVersion()); p.setString(5, event.eventType());
                        p.setString(6, event.payload()); p.setTimestamp(7, Timestamp.from(event.occurredAt()));
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                c.commit();
                return new WarehouseCommitResult(candidate, false);
            } catch (RuntimeException | SQLException e) {
                rollback(c, e);
                if (e instanceof RuntimeException re) throw re;
                SQLException sql = (SQLException) e;
                if ("23505".equals(sql.getSQLState()))
                    throw new OptimisticLockException("Concurrent warehouse command committed first");
                throw failure("commit warehouse command", sql);
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignored) { }
            }
        } catch (SQLException e) { throw failure("open warehouse transaction", e); }
    }

    private WarehouseCommandReceipt receipt(Connection c, String tenant, String key) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT fingerprint,result_payload,committed_at FROM warehouse_command_receipt WHERE tenant_id=? AND command_key=? FOR UPDATE")) {
            p.setString(1, tenant); p.setString(2, key);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                return new WarehouseCommandReceipt(tenant, key, rs.getString(1), decodeOrder(rs.getString(2)),
                        rs.getTimestamp(3).toInstant());
            }
        }
    }

    private static String encodeOrder(WarehouseWorkOrder o) {
        List<String> f = new ArrayList<>(List.of(o.id(), o.tenantId(), o.reference(), nullable(o.fulfillmentReference()),
                o.type().name(), o.status().name(), o.createdAt().toString(), o.updatedAt().toString(),
                Long.toString(o.version()), Integer.toString(o.lines().size())));
        for (WarehouseWorkLine l : o.lines()) {
            f.addAll(List.of(l.lineId(), l.itemReference(), l.uom(), l.plannedQuantity().toPlainString(),
                    l.executedQuantity().toPlainString(), nullable(l.sourceLocation()),
                    nullable(l.destinationLocation()), nullable(l.packageReference())));
        }
        return f.stream().map(JdbcWarehouseWorkRepository::b64).reduce((a,b) -> a + "." + b).orElseThrow();
    }

    private static WarehouseWorkOrder decodeOrder(String payload) {
        try {
            String[] encoded = payload.split("\\.", -1);
            List<String> f = Arrays.stream(encoded).map(JdbcWarehouseWorkRepository::unb64).toList();
            if (f.size() < 10) throw new IllegalArgumentException("Invalid warehouse snapshot");
            int count = Integer.parseInt(f.get(9));
            if (count < 1 || f.size() != 10 + count * 8) throw new IllegalArgumentException("Invalid warehouse snapshot line count");
            List<WarehouseWorkLine> lines = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int n = 10 + i * 8;
                lines.add(new WarehouseWorkLine(f.get(n), f.get(n+1), f.get(n+2),
                        new BigDecimal(f.get(n+3)), new BigDecimal(f.get(n+4)),
                        emptyToNull(f.get(n+5)), emptyToNull(f.get(n+6)), emptyToNull(f.get(n+7))));
            }
            return new WarehouseWorkOrder(f.get(0), f.get(1), f.get(2), emptyToNull(f.get(3)),
                    WarehouseWorkType.valueOf(f.get(4)), WarehouseWorkStatus.valueOf(f.get(5)),
                    lines, Instant.parse(f.get(6)), Instant.parse(f.get(7)), Long.parseLong(f.get(8)));
        } catch (RuntimeException e) { throw new IllegalStateException("Cannot decode warehouse snapshot", e); }
    }

    private static String b64(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String unb64(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
    private static String nullable(String s) { return s == null ? "" : s; }
    private static String emptyToNull(String s) { return s.isEmpty() ? null : s; }
    private static void require(String s, String field) { if (s == null || s.isBlank()) throw new IllegalArgumentException(field + " required"); }
    private static void rollback(Connection c, Exception cause) {
        try { c.rollback(); } catch (SQLException rollback) { cause.addSuppressed(rollback); }
    }
    private static RuntimeException failure(String operation, SQLException e) {
        return new IllegalStateException("Unable to " + operation + ": " + e.getMessage(), e);
    }

    /** SQLState 23505 and stale aggregate versions are surfaced as a retryable conflict. */
    public static final class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) { super(message); }
    }
}
