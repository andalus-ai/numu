package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Durable at-least-once relay. A short DB transaction creates delivery rows and leases work;
 * network publication happens outside that transaction. Expired leases are recoverable after a crash.
 */
@ApplicationScoped
public class JdbcCourierOutboxPublisher {
    private static final int MAX_ATTEMPTS = 12;
    private static final int MAX_ERROR_LENGTH = 1000;
    private final DataSource dataSource;

    @Inject
    public JdbcCourierOutboxPublisher(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int publishBatch(String destination, String workerId, int limit, CourierEventPublisher publisher) {
        if (destination == null || destination.isBlank() || destination.length() > 120)
            throw new IllegalArgumentException("destination must contain 1-120 characters");
        if (workerId == null || workerId.isBlank() || workerId.length() > 160)
            throw new IllegalArgumentException("workerId must contain 1-160 characters");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        if (publisher == null) throw new IllegalArgumentException("publisher must not be null");

        List<Delivery> deliveries = claim(destination, workerId, limit);
        int accepted = 0;
        for (Delivery delivery : deliveries) {
            try {
                publisher.publish(destination, delivery.event());
                acknowledge(delivery, destination, workerId);
                accepted++;
            } catch (Exception failure) {
                retry(delivery, destination, workerId, failure);
            }
        }
        return accepted;
    }

    private List<Delivery> claim(String destination, String workerId, int limit) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // A process can die after claiming its final allowed attempt. Reap that expired lease
                // into the terminal state instead of leaving an unclaimable PENDING row forever.
                try (PreparedStatement reap = c.prepareStatement("""
                        UPDATE courier_event_publications
                        SET status = 'DEAD', lease_owner = NULL, lease_until = NULL,
                            last_error = COALESCE(last_error, 'publication lease expired at retry limit'),
                            updated_at = now()
                        WHERE destination = ? AND status = 'PENDING'
                          AND attempt_count >= ? AND lease_until < now()
                        """)) {
                    reap.setString(1, destination);
                    reap.setInt(2, MAX_ATTEMPTS);
                    reap.executeUpdate();
                }
                // This backfill is idempotent and independent from the projector's published_at.
                try (PreparedStatement seed = c.prepareStatement("""
                        INSERT INTO courier_event_publications (tenant_id, event_id, destination)
                        SELECT e.tenant_id, e.id, ?
                        FROM courier_event_outbox e
                        WHERE NOT EXISTS (
                            SELECT 1 FROM courier_event_publications p
                            WHERE p.tenant_id = e.tenant_id AND p.event_id = e.id AND p.destination = ?
                        )
                        ORDER BY e.created_at, e.id
                        LIMIT ?
                        ON CONFLICT DO NOTHING
                        """)) {
                    seed.setString(1, destination);
                    seed.setString(2, destination);
                    seed.setInt(3, limit);
                    seed.executeUpdate();
                }
                List<Delivery> claimed = new ArrayList<>();
                String sql = """
                        SELECT p.tenant_id, p.event_id, e.event_type, e.aggregate_type,
                               e.aggregate_reference, e.occurred_at, e.payload::text AS payload_json,
                               p.attempt_count
                        FROM courier_event_publications p
                        JOIN courier_event_outbox e ON e.tenant_id = p.tenant_id AND e.id = p.event_id
                        WHERE p.destination = ? AND p.status = 'PENDING'
                          AND p.available_at <= now()
                          AND (p.lease_until IS NULL OR p.lease_until < now())
                          AND p.attempt_count < ?
                        ORDER BY p.created_at, p.event_id
                        FOR UPDATE OF p SKIP LOCKED
                        LIMIT ?
                        """;
                try (PreparedStatement select = c.prepareStatement(sql)) {
                    select.setString(1, destination);
                    select.setInt(2, MAX_ATTEMPTS);
                    select.setInt(3, limit);
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            UUID tenant = rs.getObject("tenant_id", UUID.class);
                            UUID eventId = rs.getObject("event_id", UUID.class);
                            int attempts = rs.getInt("attempt_count") + 1;
                            try (PreparedStatement lease = c.prepareStatement("""
                                    UPDATE courier_event_publications
                                    SET lease_owner = ?, lease_until = now() + interval '60 seconds',
                                        attempt_count = ?, updated_at = now()
                                    WHERE tenant_id = ? AND event_id = ? AND destination = ?
                                    """)) {
                                lease.setString(1, workerId);
                                lease.setInt(2, attempts);
                                lease.setObject(3, tenant);
                                lease.setObject(4, eventId);
                                lease.setString(5, destination);
                                if (lease.executeUpdate() != 1) throw new SQLException("unable to lease publication row");
                            }
                            CourierPublishedEvent event = new CourierPublishedEvent(
                                    tenant, eventId, rs.getString("event_type"), rs.getString("aggregate_type"),
                                    rs.getString("aggregate_reference"), rs.getTimestamp("occurred_at").toInstant(),
                                    rs.getString("payload_json"));
                            claimed.add(new Delivery(tenant, eventId, attempts, event));
                        }
                    }
                }
                c.commit();
                return claimed;
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to claim courier outbox publications", e);
        }
    }

    private void acknowledge(Delivery d, String destination, String workerId) {
        executeLeaseUpdate("""
                UPDATE courier_event_publications
                SET status = 'PUBLISHED', published_at = now(), lease_owner = NULL, lease_until = NULL,
                    last_error = NULL, updated_at = now()
                WHERE tenant_id = ? AND event_id = ? AND destination = ?
                  AND status = 'PENDING' AND lease_owner = ?
                """, d, destination, workerId, null, null, null);
    }

    private void retry(Delivery d, String destination, String workerId, Exception failure) {
        String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        if (message.length() > MAX_ERROR_LENGTH) message = message.substring(0, MAX_ERROR_LENGTH);
        boolean dead = d.attempt() >= MAX_ATTEMPTS;
        int delaySeconds = (int) Math.min(3600L, 5L * (1L << Math.min(10, Math.max(0, d.attempt() - 1))));
        executeLeaseUpdate("""
                UPDATE courier_event_publications
                SET status = ?, available_at = now() + (? * interval '1 second'),
                    lease_owner = NULL, lease_until = NULL, last_error = ?, updated_at = now()
                WHERE tenant_id = ? AND event_id = ? AND destination = ?
                  AND status = 'PENDING' AND lease_owner = ?
                """, d, destination, workerId, dead ? "DEAD" : "PENDING", delaySeconds, message);
    }

    private void executeLeaseUpdate(String sql, Delivery d, String destination, String workerId,
                                    String status, Integer delaySeconds, String error) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            int i = 1;
            if (status != null) s.setString(i++, status);
            if (delaySeconds != null) s.setInt(i++, delaySeconds);
            if (error != null) s.setString(i++, error);
            s.setObject(i++, d.tenantId());
            s.setObject(i++, d.eventId());
            s.setString(i++, destination);
            s.setString(i, workerId);
            if (s.executeUpdate() != 1) throw new SQLException("publication lease was lost before acknowledgement");
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update courier publication state", e);
        }
    }

    private record Delivery(UUID tenantId, UUID eventId, int attempt, CourierPublishedEvent event) {}
}
