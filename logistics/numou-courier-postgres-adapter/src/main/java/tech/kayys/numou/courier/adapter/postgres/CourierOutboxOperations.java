package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Operational query and replay service. No default allow policy is provided: a deployment must
 * supply a real authorization adapter before operational access is enabled.
 */
@ApplicationScoped
public class CourierOutboxOperations {
    private final DataSource dataSource;
    private final Instance<CourierOutboxOperationsAuthorizer> authorizers;

    @Inject
    public CourierOutboxOperations(DataSource dataSource,
                                   Instance<CourierOutboxOperationsAuthorizer> authorizers) {
        this.dataSource = dataSource;
        this.authorizers = authorizers;
    }

    public List<DestinationHealth> health(String actorReference, String destination) {
        validateActor(actorReference);
        validateDestination(destination);
        CourierOutboxOperationsAuthorizer authorizer = requireAuthorizer();
        if (!authorizer.mayInspect(actorReference, destination))
            throw new SecurityException("actor is not authorized to inspect this courier destination");
        String sql = """
                SELECT status,
                       count(*) AS total,
                       count(*) FILTER (WHERE status = 'PENDING' AND lease_until IS NOT NULL AND lease_until >= now()) AS leased,
                       count(*) FILTER (WHERE status = 'PENDING' AND available_at <= now()
                                           AND (lease_until IS NULL OR lease_until < now())) AS ready,
                       min(created_at) FILTER (WHERE status = 'PENDING') AS oldest_pending_at,
                       max(updated_at) AS last_state_change_at
                FROM courier_event_publications
                WHERE destination = ?
                GROUP BY status
                ORDER BY status
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, destination);
            List<DestinationHealth> result = new ArrayList<>();
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    Timestamp oldest = rs.getTimestamp("oldest_pending_at");
                    Timestamp changed = rs.getTimestamp("last_state_change_at");
                    result.add(new DestinationHealth(destination, rs.getString("status"), rs.getLong("total"),
                            rs.getLong("ready"), rs.getLong("leased"),
                            oldest == null ? null : oldest.toInstant(), changed == null ? null : changed.toInstant()));
                }
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read courier outbox health", e);
        }
    }

    /** Requeue only a terminal DEAD row; each accepted replay is audited in the same transaction. */
    public ReplayResult replayDeadLetter(String actorReference, String reason,
                                        UUID tenantId, UUID eventId, String destination) {
        validateActor(actorReference);
        validateDestination(destination);
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw new IllegalArgumentException("reason must contain 1-1000 characters");
        if (tenantId == null || eventId == null) throw new IllegalArgumentException("tenantId and eventId are required");
        CourierOutboxOperationsAuthorizer authorizer = requireAuthorizer();
        if (!authorizer.mayReplay(actorReference, destination, tenantId.toString(), eventId.toString()))
            throw new SecurityException("actor is not authorized to replay this courier publication");

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String lockSql = """
                        SELECT status, attempt_count FROM courier_event_publications
                        WHERE tenant_id = ? AND event_id = ? AND destination = ?
                        FOR UPDATE
                        """;
                String previousStatus;
                int previousAttempts;
                try (PreparedStatement lock = c.prepareStatement(lockSql)) {
                    lock.setObject(1, tenantId);
                    lock.setObject(2, eventId);
                    lock.setString(3, destination);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("courier publication does not exist");
                        previousStatus = rs.getString("status");
                        previousAttempts = rs.getInt("attempt_count");
                    }
                }
                if (!"DEAD".equals(previousStatus))
                    throw new IllegalStateException("only DEAD courier publications can be replayed");

                try (PreparedStatement update = c.prepareStatement("""
                        UPDATE courier_event_publications
                        SET status = 'PENDING', attempt_count = 0, available_at = now(),
                            lease_owner = NULL, lease_until = NULL, published_at = NULL,
                            last_error = NULL, replay_count = replay_count + 1,
                            last_replayed_at = now(), last_replayed_by = ?, updated_at = now()
                        WHERE tenant_id = ? AND event_id = ? AND destination = ? AND status = 'DEAD'
                        """)) {
                    update.setString(1, actorReference);
                    update.setObject(2, tenantId);
                    update.setObject(3, eventId);
                    update.setString(4, destination);
                    if (update.executeUpdate() != 1) throw new SQLException("publication state changed during replay");
                }

                try (PreparedStatement audit = c.prepareStatement("""
                        INSERT INTO courier_event_publication_audit
                          (audit_id, tenant_id, event_id, destination, action, actor_reference,
                           reason, previous_status, previous_attempt_count)
                        VALUES (?, ?, ?, ?, 'REPLAY_REQUESTED', ?, ?, ?, ?)
                        """)) {
                    audit.setObject(1, UUID.randomUUID());
                    audit.setObject(2, tenantId);
                    audit.setObject(3, eventId);
                    audit.setString(4, destination);
                    audit.setString(5, actorReference);
                    audit.setString(6, reason.trim());
                    audit.setString(7, previousStatus);
                    audit.setInt(8, previousAttempts);
                    audit.executeUpdate();
                }
                c.commit();
                return new ReplayResult(tenantId, eventId, destination, "PENDING", previousAttempts);
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to replay courier publication", e);
        }
    }

    private CourierOutboxOperationsAuthorizer requireAuthorizer() {
        if (authorizers.isUnsatisfied() || authorizers.isAmbiguous())
            throw new IllegalStateException("A unique CourierOutboxOperationsAuthorizer must be configured");
        return authorizers.get();
    }

    private static void validateActor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 200)
            throw new IllegalArgumentException("actorReference must contain 1-200 characters");
    }

    private static void validateDestination(String destination) {
        if (destination == null || destination.isBlank() || destination.length() > 120)
            throw new IllegalArgumentException("destination must contain 1-120 characters");
    }

    public record DestinationHealth(String destination, String status, long total, long ready, long leased,
                                    java.time.Instant oldestPendingAt, java.time.Instant lastStateChangeAt) {}
    public record ReplayResult(UUID tenantId, UUID eventId, String destination, String status,
                               int previousAttemptCount) {}
}
