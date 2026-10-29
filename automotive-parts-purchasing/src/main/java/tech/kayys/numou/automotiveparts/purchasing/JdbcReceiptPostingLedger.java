package tech.kayys.numou.automotiveparts.purchasing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * PostgreSQL-compatible JDBC implementation. Apply V1__receipt_posting_ledger.sql
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
                SET status = 'POSTED', movement_id = ?, last_failure = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND receipt_id = ? AND payload_fingerprint = ?
                """, movementId, tenantId, receiptId, fingerprint);
    }

    @Override
    public void markRetryable(String tenantId, String receiptId, String fingerprint, String failure) {
        update(tenantId, receiptId, fingerprint, """
                UPDATE purchasing_receipt_inventory_posting
                SET last_failure = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND receipt_id = ? AND payload_fingerprint = ? AND status = 'PENDING'
                """, failure == null ? "Unknown posting failure" : failure, tenantId, receiptId, fingerprint);
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
