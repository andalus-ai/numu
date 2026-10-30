package tech.kayys.numou.automotiveparts.purchasing;

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

/** PostgreSQL JDBC ledger. Requires migrations V1000-V1002. */
public final class JdbcReceiptPostingLedger implements ReceiptPostingLedger {
    private final DataSource dataSource;
    public JdbcReceiptPostingLedger(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override public PostingRecord begin(String tenantId, String receiptId, String fingerprint) {
        return begin(tenantId, receiptId, fingerprint, DEFAULT_MAX_ATTEMPTS);
    }

    @Override public PostingRecord begin(String tenantId, String receiptId, String fingerprint, int maxAttempts) {
        require(tenantId, "tenantId"); require(receiptId, "receiptId"); require(fingerprint, "fingerprint");
        requireMaxAttempts(maxAttempts);
        return tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO purchasing_receipt_inventory_posting
                      (tenant_id, receipt_id, payload_fingerprint, status, attempts, max_attempts, created_at, updated_at)
                    VALUES (?, ?, ?, 'PENDING', 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (tenant_id, receipt_id) DO NOTHING
                    """)) {
                ps.setString(1, tenantId); ps.setString(2, receiptId); ps.setString(3, fingerprint);
                ps.setInt(4, maxAttempts); ps.executeUpdate();
            }
            PostingRecord record = select(c, tenantId, receiptId, true);
            if (!record.payloadFingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("Goods receipt payload changed for an existing tenant/receipt identity");
            }
            return record;
        });
    }

    @Override public void markPosted(String tenantId, String receiptId, String fingerprint, String movementId) {
        require(movementId, "movementId");
        updateOne("""
                UPDATE purchasing_receipt_inventory_posting
                SET status='POSTED', movement_id=?, last_failure=NULL, terminal_failure_reason=NULL,
                    dead_lettered_at=NULL, claimed_by=NULL, lease_until=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND receipt_id=? AND payload_fingerprint=?
                """, movementId, tenantId, receiptId, fingerprint);
    }

    @Override public void markRetryable(String tenantId, String receiptId, String fingerprint, String failure) {
        markRetryable(tenantId, receiptId, fingerprint, failure, DEFAULT_MAX_ATTEMPTS);
    }

    @Override public void markRetryable(String tenantId, String receiptId, String fingerprint,
                                        String failure, int maxAttempts) {
        require(tenantId, "tenantId"); require(receiptId, "receiptId"); require(fingerprint, "fingerprint");
        requireMaxAttempts(maxAttempts);
        tx(c -> {
            String detail = failure == null || failure.isBlank() ? "Unknown posting failure" : failure;
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE purchasing_receipt_inventory_posting
                    SET attempts=attempts+1,
                        status=CASE WHEN attempts+1 >= max_attempts THEN 'DEAD_LETTER' ELSE 'PENDING' END,
                        last_failure=?,
                        terminal_failure_reason=CASE WHEN attempts+1 >= max_attempts THEN ? ELSE NULL END,
                        dead_lettered_at=CASE WHEN attempts+1 >= max_attempts THEN CURRENT_TIMESTAMP ELSE NULL END,
                        claimed_by=NULL, lease_until=NULL, updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND receipt_id=? AND payload_fingerprint=? AND status='PENDING'
                    """)) {
                ps.setString(1, detail); ps.setString(2, detail); ps.setString(3, tenantId);
                ps.setString(4, receiptId); ps.setString(5, fingerprint);
                if (ps.executeUpdate() != 1) {
                    PostingRecord current = select(c, tenantId, receiptId, true);
                    if (!current.payloadFingerprint().equals(fingerprint) || current.status() == Status.PENDING) {
                        throw new IllegalStateException("Posting retry transition lost or fingerprint mismatch");
                    }
                }
            }
            return null;
        });
    }

    @Override public List<PostingRecord> findPending(Instant updatedBefore, int limit) {
        Objects.requireNonNull(updatedBefore, "updatedBefore is required"); requireLimit(limit);
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT tenant_id, receipt_id, payload_fingerprint, status, movement_id, attempts,
                       last_failure, dead_lettered_at, requeued_at, requeued_by, requeue_reason
                FROM purchasing_receipt_inventory_posting
                WHERE status='PENDING' AND updated_at <= ?
                ORDER BY updated_at, tenant_id, receipt_id LIMIT ?
                """)) {
            ps.setTimestamp(1, Timestamp.from(updatedBefore)); ps.setInt(2, limit);
            List<PostingRecord> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(read(rs)); }
            return List.copyOf(result);
        } catch (SQLException e) { throw new IllegalStateException("Could not query pending goods receipt postings", e); }
    }

    @Override public List<PostingRecord> claimPending(Instant updatedBefore, String workerId, Instant leaseUntil, int limit) {
        Objects.requireNonNull(updatedBefore, "updatedBefore is required");
        Objects.requireNonNull(leaseUntil, "leaseUntil is required");
        require(workerId, "workerId"); requireLimit(limit);
        if (!leaseUntil.isAfter(Instant.now())) throw new IllegalArgumentException("leaseUntil must be in the future");
        return tx(c -> {
            List<PostingRecord> claimed = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT tenant_id, receipt_id, payload_fingerprint, status, movement_id, attempts,
                           last_failure, dead_lettered_at, requeued_at, requeued_by, requeue_reason
                    FROM purchasing_receipt_inventory_posting
                    WHERE status='PENDING' AND updated_at <= ?
                      AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY updated_at, tenant_id, receipt_id LIMIT ? FOR UPDATE SKIP LOCKED
                    """)) {
                ps.setTimestamp(1, Timestamp.from(updatedBefore)); ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) claimed.add(read(rs)); }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE purchasing_receipt_inventory_posting SET claimed_by=?, lease_until=?
                    WHERE tenant_id=? AND receipt_id=? AND status='PENDING'
                    """)) {
                for (PostingRecord r : claimed) {
                    ps.setString(1, workerId); ps.setTimestamp(2, Timestamp.from(leaseUntil));
                    ps.setString(3, r.tenantId()); ps.setString(4, r.receiptId()); ps.addBatch();
                }
                ps.executeBatch();
            }
            return List.copyOf(claimed);
        });
    }

    @Override public void requeue(String tenantId, String receiptId, String fingerprint, String actorId, String reason) {
        require(tenantId, "tenantId"); require(receiptId, "receiptId"); require(fingerprint, "fingerprint");
        require(actorId, "actorId"); require(reason, "reason");
        updateOne("""
                UPDATE purchasing_receipt_inventory_posting
                SET status='PENDING', attempts=0, last_failure=NULL, terminal_failure_reason=NULL,
                    dead_lettered_at=NULL, requeued_at=CURRENT_TIMESTAMP, requeued_by=?, requeue_reason=?,
                    claimed_by=NULL, lease_until=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND receipt_id=? AND payload_fingerprint=? AND status='DEAD_LETTER'
                """, actorId.trim(), reason.trim(), tenantId, receiptId, fingerprint);
    }

    private PostingRecord select(Connection c, String tenantId, String receiptId, boolean lock) throws SQLException {
        String sql = """
                SELECT tenant_id, receipt_id, payload_fingerprint, status, movement_id, attempts,
                       last_failure, dead_lettered_at, requeued_at, requeued_by, requeue_reason
                FROM purchasing_receipt_inventory_posting WHERE tenant_id=? AND receipt_id=?
                """ + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId); ps.setString(2, receiptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Posting record not found");
                return read(rs);
            }
        }
    }

    private void updateOne(String sql, String... values) {
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) ps.setString(i + 1, values[i]);
                if (ps.executeUpdate() != 1) throw new IllegalStateException("Posting record missing, state changed, or fingerprint mismatch");
            }
            return null;
        });
    }

    private <T> T tx(SqlWork<T> work) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try { T result = work.apply(c); c.commit(); return result; }
            catch (RuntimeException | SQLException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Goods receipt posting ledger operation failed", e);
            }
        } catch (SQLException e) { throw new IllegalStateException("Could not access goods receipt posting ledger", e); }
    }

    private static PostingRecord read(ResultSet rs) throws SQLException {
        Timestamp dead = rs.getTimestamp("dead_lettered_at");
        Timestamp requeued = rs.getTimestamp("requeued_at");
        return new PostingRecord(rs.getString("tenant_id"), rs.getString("receipt_id"),
                rs.getString("payload_fingerprint"), Status.valueOf(rs.getString("status")),
                rs.getString("movement_id"), rs.getInt("attempts"), rs.getString("last_failure"),
                dead == null ? null : dead.toInstant(), requeued == null ? null : requeued.toInstant(),
                rs.getString("requeued_by"), rs.getString("requeue_reason"));
    }
    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
    private static void requireMaxAttempts(int value) {
        if (value < 1 || value > 100) throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
    }
    private static void requireLimit(int value) {
        if (value < 1 || value > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
    }
    @FunctionalInterface private interface SqlWork<T> { T apply(Connection connection) throws SQLException; }
}
