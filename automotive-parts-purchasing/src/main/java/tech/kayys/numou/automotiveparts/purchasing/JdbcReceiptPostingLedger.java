package tech.kayys.numou.automotiveparts.purchasing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL-compatible JDBC implementation. Apply V1000__receipt_posting_ledger.sql and V1001__receipt_posting_worker_leases.sql
 * through the deployment's normal migration runner before enabling this adapter.
 */
public final class JdbcReceiptPostingLedger implements ReceiptPostingLedger {
    private final DataSource dataSource;

    public JdbcReceiptPostingLedger(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public PostingRecord begin(String tenantId, String receiptId, String fingerprint) {
        require(tenantId, "tenantId"); require(receiptId, "receiptId"); require(fingerprint, "fingerprint");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement insert = c.prepareStatement("""
                        INSERT INTO purchasing_receipt_inventory_posting
                          (tenant_id, receipt_id, payload_fingerprint, status, attempts, created_at, updated_at)
                        VALUES (?, ?, ?, 'PENDING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (tenant_id, receipt_id) DO UPDATE
                          SET attempts = purchasing_receipt_inventory_posting.attempts + 1,
                              updated_at = CURRENT_TIMESTAMP
                        """)) {
                    insert.setString(1, tenantId);
                    insert.setString(2, receiptId);
                    insert.setString(3, fingerprint);
                    insert.executeUpdate();
                }
                PostingRecord record;
                try (PreparedStatement select = c.prepareStatement("""
                        SELECT tenant_id, receipt_id, payload_fingerprint, status, movement_id, attempts, last_failure
                        FROM purchasing_receipt_inventory_posting
                        WHERE tenant_id = ? AND receipt_id = ? FOR UPDATE
                        """)) {
                    select.setString(1, tenantId); select.setString(2, receiptId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Posting record disappeared during claim");
                        record = read(rs);
                    }
                }
                if (!record.payloadFingerprint().equals(fingerprint)) {
                    throw new IllegalArgumentException("Goods receipt payload changed for an existing tenant/receipt identity");
                }
                c.commit();
                return record;
            } catch (RuntimeException | SQLException e) {
                rollback(c, e);
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Could not begin goods receipt posting", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not access goods receipt posting ledger", e);
        }
    }

    @Override
    public void markPosted(String tenantId, String receiptId, String fingerprint, String movementId) {
        require(movementId, "movementId");
        update(tenantId, receiptId, fingerprint, """
                UPDATE purchasing_receipt_inventory_posting
                SET status = 'POSTED', movement_id = ?, last_failure = NULL, claimed_by = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND receipt_id = ? AND payload_fingerprint = ?
                """, movementId, tenantId, receiptId, fingerprint);
    }

    @Override
    public void markRetryable(String tenantId, String receiptId, String fingerprint, String failure) {
        update(tenantId, receiptId, fingerprint, """
                UPDATE purchasing_receipt_inventory_posting
                SET last_failure = ?, claimed_by = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND receipt_id = ? AND payload_fingerprint = ? AND status = 'PENDING'
                """, failure == null ? "Unknown posting failure" : failure, tenantId, receiptId, fingerprint);
    }

    @Override
    public List<PostingRecord> findPending(Instant updatedBefore, int limit) {
        Objects.requireNonNull(updatedBefore, "updatedBefore is required");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT tenant_id, receipt_id, payload_fingerprint, status, movement_id, attempts, last_failure
                     FROM purchasing_receipt_inventory_posting
                     WHERE status = 'PENDING' AND updated_at <= ?
                     ORDER BY updated_at ASC, tenant_id ASC, receipt_id ASC
                     LIMIT ?
                     """)) {
            ps.setTimestamp(1, Timestamp.from(updatedBefore));
            ps.setInt(2, limit);
            List<PostingRecord> records = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) records.add(read(rs));
            }
            return List.copyOf(records);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query pending goods receipt postings", e);
        }
    }

    @Override
    public List<PostingRecord> claimPending(Instant updatedBefore, String workerId, Instant leaseUntil, int limit) {
        Objects.requireNonNull(updatedBefore, "updatedBefore is required");
        Objects.requireNonNull(leaseUntil, "leaseUntil is required");
        require(workerId, "workerId");
        if (!leaseUntil.isAfter(Instant.now())) throw new IllegalArgumentException("leaseUntil must be in the future");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<PostingRecord> claimed = new ArrayList<>();
                try (PreparedStatement select = c.prepareStatement("""
                        SELECT tenant_id, receipt_id, payload_fingerprint, status, movement_id, attempts, last_failure
                        FROM purchasing_receipt_inventory_posting
                        WHERE status = 'PENDING' AND updated_at <= ?
                          AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                        ORDER BY updated_at ASC, tenant_id ASC, receipt_id ASC
                        LIMIT ? FOR UPDATE SKIP LOCKED
                        """)) {
                    select.setTimestamp(1, Timestamp.from(updatedBefore));
                    select.setInt(2, limit);
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) claimed.add(read(rs));
                    }
                }
                try (PreparedStatement update = c.prepareStatement("""
                        UPDATE purchasing_receipt_inventory_posting
                        SET claimed_by = ?, lease_until = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE tenant_id = ? AND receipt_id = ? AND status = 'PENDING'
                        """)) {
                    for (PostingRecord record : claimed) {
                        update.setString(1, workerId);
                        update.setTimestamp(2, Timestamp.from(leaseUntil));
                        update.setString(3, record.tenantId());
                        update.setString(4, record.receiptId());
                        update.addBatch();
                    }
                    update.executeBatch();
                }
                c.commit();
                return List.copyOf(claimed);
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Could not claim pending goods receipt postings", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not access goods receipt posting ledger", e);
        }
    }

    private void update(String tenantId, String receiptId, String fingerprint, String sql, String... values) {
        require(tenantId, "tenantId"); require(receiptId, "receiptId"); require(fingerprint, "fingerprint");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setString(i + 1, values[i]);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Posting ledger record missing or fingerprint mismatch");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update goods receipt posting ledger", e);
        }
    }

    private static PostingRecord read(ResultSet rs) throws SQLException {
        return new PostingRecord(rs.getString("tenant_id"), rs.getString("receipt_id"),
                rs.getString("payload_fingerprint"), Status.valueOf(rs.getString("status")),
                rs.getString("movement_id"), rs.getInt("attempts"), rs.getString("last_failure"));
    }

    private static void rollback(Connection c, Exception cause) {
        try { c.rollback(); } catch (SQLException rollbackFailure) { cause.addSuppressed(rollbackFailure); }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
