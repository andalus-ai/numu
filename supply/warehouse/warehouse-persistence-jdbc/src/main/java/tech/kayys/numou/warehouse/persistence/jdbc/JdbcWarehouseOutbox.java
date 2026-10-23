package tech.kayys.numou.warehouse.persistence.jdbc;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** PostgreSQL outbox dispatcher port with bounded, lease-based claims for multiple workers. */
public final class JdbcWarehouseOutbox {
    private final DataSource dataSource;
    public JdbcWarehouseOutbox(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    /**
     * Claims a bounded batch. SKIP LOCKED prevents concurrent workers selecting the same
     * rows in one transaction; claim_until makes claims recoverable after worker crashes.
     */
    public List<OutboxRecord> claim(String workerId, int limit, Duration lease) {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId required");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        if (lease == null || lease.isZero() || lease.isNegative() || lease.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("lease must be positive and no longer than one hour");
        Instant now = Instant.now(), until = now.plus(lease);
        String sql = "WITH selected AS (SELECT event_id FROM warehouse_outbox_event WHERE published_at IS NULL AND (claim_until IS NULL OR claim_until < ?) ORDER BY occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT ?) " +
                "UPDATE warehouse_outbox_event e SET claim_owner=?,claim_until=?,delivery_attempts=e.delivery_attempts+1 FROM selected s WHERE e.event_id=s.event_id " +
                "RETURNING e.event_id,e.tenant_id,e.aggregate_id,e.aggregate_version,e.event_type,e.payload,e.occurred_at,e.delivery_attempts";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setTimestamp(1, Timestamp.from(now)); p.setInt(2, limit); p.setString(3, workerId.trim());
                p.setTimestamp(4, Timestamp.from(until));
                List<OutboxRecord> result = new ArrayList<>();
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) result.add(new OutboxRecord(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getLong(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant(),
                            rs.getInt(8), workerId.trim(), until));
                }
                c.commit(); return List.copyOf(result);
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw new IllegalStateException("Unable to claim warehouse outbox events", e); }
    }

    public void markPublished(String eventId, String workerId) {
        updateClaimed("UPDATE warehouse_outbox_event SET published_at=?,claim_owner=NULL,claim_until=NULL,last_error=NULL WHERE event_id=? AND claim_owner=? AND published_at IS NULL",
                Timestamp.from(Instant.now()), eventId, workerId, null);
    }

    public void releaseForRetry(String eventId, String workerId, String error) {
        String safeError = error == null ? "Unspecified publishing failure" : error;
        if (safeError.length() > 4000) safeError = safeError.substring(0, 4000);
        updateClaimed("UPDATE warehouse_outbox_event SET claim_owner=NULL,claim_until=NULL,last_error=? WHERE event_id=? AND claim_owner=? AND published_at IS NULL",
                safeError, eventId, workerId, null);
    }

    private void updateClaimed(String sql, Object first, String eventId, String workerId, Object ignored) {
        if (eventId == null || eventId.isBlank() || workerId == null || workerId.isBlank())
            throw new IllegalArgumentException("eventId and workerId required");
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            if (first instanceof Timestamp timestamp) p.setTimestamp(1, timestamp); else p.setString(1, String.valueOf(first));
            p.setString(2, eventId); p.setString(3, workerId);
            if (p.executeUpdate() != 1) throw new IllegalStateException("Outbox claim lost, expired or already published");
        } catch (SQLException e) { throw new IllegalStateException("Unable to update warehouse outbox claim", e); }
    }

    public record OutboxRecord(String eventId, String tenantId, String aggregateId, long aggregateVersion,
                               String eventType, String payload, Instant occurredAt, int deliveryAttempts,
                               String claimOwner, Instant claimUntil) { }
}
