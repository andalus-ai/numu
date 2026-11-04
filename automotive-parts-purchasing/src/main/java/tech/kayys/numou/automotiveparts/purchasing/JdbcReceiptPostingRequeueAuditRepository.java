package tech.kayys.numou.automotiveparts.purchasing;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Read-only tenant-scoped access to the append-only receipt posting requeue audit. */
public final class JdbcReceiptPostingRequeueAuditRepository {
    private final DataSource dataSource;

    public JdbcReceiptPostingRequeueAuditRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    public List<ReceiptPostingRequeueAudit> findByReceipt(String tenantId, String receiptId, int limit) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (receiptId == null || receiptId.isBlank()) throw new IllegalArgumentException("receiptId is required");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT audit_id, tenant_id, receipt_id, payload_fingerprint, actor_id, reason, occurred_at
                     FROM purchasing_receipt_inventory_posting_requeue_audit
                     WHERE tenant_id = ? AND receipt_id = ?
                     ORDER BY occurred_at DESC, audit_id DESC
                     LIMIT ?
                     """)) {
            statement.setString(1, tenantId);
            statement.setString(2, receiptId);
            statement.setInt(3, limit);
            List<ReceiptPostingRequeueAudit> events = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp occurredAt = rs.getTimestamp("occurred_at");
                    events.add(new ReceiptPostingRequeueAudit(
                            rs.getObject("audit_id", UUID.class),
                            rs.getString("tenant_id"),
                            rs.getString("receipt_id"),
                            rs.getString("payload_fingerprint"),
                            rs.getString("actor_id"),
                            rs.getString("reason"),
                            occurredAt.toInstant()));
                }
            }
            return List.copyOf(events);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query receipt posting requeue audit", e);
        }
    }
}
