package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
import java.util.UUID;

/**
 * PostgreSQL lease-based relay store for schedule-change outbox rows.
 *
 * <p>Claiming is safe across concurrent workers: rows are locked with SKIP LOCKED and
 * a lease token fences acknowledgements from workers whose leases have expired. Publishing
 * itself is deliberately outside this class; callers publish the returned payload and then
 * acknowledge it. Delivery is at-least-once, so consumers must deduplicate by eventId.
 */
@ApplicationScoped
public class JdbcScheduleChangeOutboxRelayStore {
    private final DataSource dataSource;

    @Inject
    public JdbcScheduleChangeOutboxRelayStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    /**
     * Claims due rows in creation order. A single token is used for the batch; each row is
     * independently identified by outboxId. Expired leases may be reclaimed.
     */
    public List<ClaimedEvent> claimDue(int limit, Instant now, Instant leaseUntil, UUID claimToken) {
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil cannot be null");
        Objects.requireNonNull(claimToken, "claimToken cannot be null");
        if (!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");

        String sql = """
                WITH due AS (
                    SELECT outbox_id
                    FROM schedule_change_outbox
                    WHERE published_at IS NULL
                      AND available_at <= ?
                      AND (claim_token IS NULL OR claimed_until <= ?)
                      AND attempt_count < 10
                    ORDER BY created_at, outbox_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE schedule_change_outbox o
                SET claim_token = ?, claimed_until = ?, attempt_count = o.attempt_count + 1
                FROM due
                WHERE o.outbox_id = due.outbox_id
                RETURNING o.outbox_id, o.execution_id, o.event_type, o.tenant_id,
                          o.structure_id, o.plan_id, o.payload::text AS payload,
                          o.occurred_at, o.attempt_count
                """;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setInt(3, limit);
                ps.setObject(4, claimToken);
                ps.setTimestamp(5, Timestamp.from(leaseUntil));
                List<ClaimedEvent> claimed = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        claimed.add(new ClaimedEvent(
                                (UUID) rs.getObject("outbox_id"),
                                (UUID) rs.getObject("execution_id"),
                                rs.getString("event_type"),
                                (UUID) rs.getObject("tenant_id"),
                                (UUID) rs.getObject("structure_id"),
                                (UUID) rs.getObject("plan_id"),
                                rs.getString("payload"),
                                rs.getTimestamp("occurred_at").toInstant(),
                                rs.getInt("attempt_count"),
                                claimToken));
                    }
                }
                c.commit();
                return List.copyOf(claimed);
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to claim schedule-change outbox events", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to claim schedule-change outbox events", e);
        }
    }

    /** Marks a row published only when the caller still owns its lease. */
    public boolean markPublished(UUID outboxId, UUID claimToken, Instant publishedAt) {
        String sql = """
                UPDATE schedule_change_outbox
                SET published_at = ?, claim_token = NULL, claimed_until = NULL, last_error = NULL
                WHERE outbox_id = ? AND claim_token = ? AND published_at IS NULL AND claimed_until > ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            Instant acknowledgedAt = Objects.requireNonNull(publishedAt, "publishedAt cannot be null");
            ps.setTimestamp(1, Timestamp.from(acknowledgedAt));
            ps.setObject(2, Objects.requireNonNull(outboxId, "outboxId cannot be null"));
            ps.setObject(3, Objects.requireNonNull(claimToken, "claimToken cannot be null"));
            ps.setTimestamp(4, Timestamp.from(acknowledgedAt));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acknowledge schedule-change outbox event", e);
        }
    }

    /**
     * Releases a failed delivery for retry. Error text is bounded before persistence and
     * retryAt must be explicit so the relay policy remains testable and deterministic.
     */
    public boolean recordFailure(UUID outboxId, UUID claimToken, Instant retryAt, String error) {
        String boundedError = error == null ? "Unknown delivery failure"
                : error.substring(0, Math.min(error.length(), 2000));
        String sql = """
                UPDATE schedule_change_outbox
                SET available_at = ?, last_error = ?, claim_token = NULL, claimed_until = NULL
                WHERE outbox_id = ? AND claim_token = ? AND published_at IS NULL AND claimed_until > CURRENT_TIMESTAMP
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Objects.requireNonNull(retryAt, "retryAt cannot be null")));
            ps.setString(2, boundedError);
            ps.setObject(3, Objects.requireNonNull(outboxId, "outboxId cannot be null"));
            ps.setObject(4, Objects.requireNonNull(claimToken, "claimToken cannot be null"));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to reschedule schedule-change outbox event", e);
        }
    }

    public record ClaimedEvent(UUID outboxId, UUID executionId, String eventType, UUID tenantId,
                               UUID structureId, UUID planId, String payload, Instant occurredAt,
                               int attemptCount, UUID claimToken) {
        public ClaimedEvent {
            Objects.requireNonNull(outboxId);
            Objects.requireNonNull(executionId);
            Objects.requireNonNull(eventType);
            Objects.requireNonNull(tenantId);
            Objects.requireNonNull(structureId);
            Objects.requireNonNull(planId);
            Objects.requireNonNull(payload);
            Objects.requireNonNull(occurredAt);
            Objects.requireNonNull(claimToken);
            if (!eventType.equals("ScheduleChangeApplied") && !eventType.equals("ScheduleChangeConflicted")) {
                throw new IllegalArgumentException("Unsupported schedule-change event type");
            }
        }
    }
}
