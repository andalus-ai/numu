package tech.kayys.numou.history.graph.postgres;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.RebuildRun;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.ReplayEvent;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL-backed, scope-isolated rebuild checkpoints and keyset replay pages. */
public final class JdbcHistoricalProjectionRebuildStore implements HistoricalProjectionRebuildStore {
    private final DataSource source;

    public JdbcHistoricalProjectionRebuildStore(DataSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public RebuildRun start(UUID tenantId, Instant now) {
        return start(tenantId, UUID.randomUUID(), now);
    }

    @Override
    public RebuildRun start(UUID tenantId, UUID runId, Instant now) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(now, "now");
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                Optional<RebuildRun> existing = find(c, tenantId, runId);
                if (existing.isPresent()) {
                    c.commit();
                    return existing.get();
                }
                // SHARE conflicts with outbox INSERT's ROW EXCLUSIVE lock. Once acquired,
                // all earlier event transactions have committed; later inserts receive
                // a sequence above this run's immutable high-water mark.
                try (Statement lock = c.createStatement()) {
                    lock.execute("LOCK TABLE historical_relationship_projection_outbox IN SHARE MODE");
                }
                existing = find(c, tenantId, runId);
                if (existing.isPresent()) {
                    c.commit();
                    return existing.get();
                }
                long highWater;
                try (Statement s = c.createStatement();
                     ResultSet r = s.executeQuery("SELECT COALESCE(MAX(projection_sequence), 0) FROM historical_relationship_projection_outbox")) {
                    r.next();
                    highWater = r.getLong(1);
                }
                try (PreparedStatement s = c.prepareStatement("""
                        INSERT INTO historical_relationship_projection_rebuild_run
                            (run_id, tenant_id, high_water_sequence, cursor_sequence, status, created_at, updated_at)
                        VALUES (?, ?, ?, 0, 'RUNNING', ?, ?)
                        """)) {
                    s.setObject(1, runId);
                    s.setObject(2, tenantId);
                    s.setLong(3, highWater);
                    s.setTimestamp(4, Timestamp.from(now));
                    s.setTimestamp(5, Timestamp.from(now));
                    s.executeUpdate();
                }
                c.commit();
                return new RebuildRun(runId, tenantId, highWater, 0, RebuildRun.Status.RUNNING, now, now);
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                throw e;
            }
        } catch (SQLException e) {
            // A concurrent retry may have won the primary-key race. Resolve only if the
            // existing run belongs to exactly the requested scope.
            Optional<RebuildRun> existing = find(tenantId, runId);
            if (existing.isPresent()) return existing.get();
            throw failure("start historical projection rebuild", e);
        }
    }

    @Override
    public Optional<RebuildRun> find(UUID tenantId, UUID runId) {
        Objects.requireNonNull(runId, "runId");
        try (Connection c = source.getConnection()) {
            return find(c, tenantId, runId);
        } catch (SQLException e) {
            throw failure("load historical projection rebuild", e);
        }
    }

    private Optional<RebuildRun> find(Connection c, UUID tenantId, UUID runId) throws SQLException {
        String sql = "SELECT * FROM historical_relationship_projection_rebuild_run WHERE run_id=? AND tenant_id IS NOT DISTINCT FROM ?";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, runId);
            s.setObject(2, tenantId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapRun(r)) : Optional.empty();
            }
        }
    }

    @Override
    public List<ReplayEvent> nextPage(UUID tenantId, UUID runId, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        String sql = """
                SELECT o.projection_sequence, o.event_id, o.event_key, o.event_type, o.schema_version,
                       o.tenant_id, o.aggregate_id, o.aggregate_revision, o.payload::text, o.occurred_at
                  FROM historical_relationship_projection_rebuild_run r
                  JOIN historical_relationship_projection_outbox o
                    ON o.tenant_id IS NOT DISTINCT FROM r.tenant_id
                   AND o.projection_sequence > r.cursor_sequence
                   AND o.projection_sequence <= r.high_water_sequence
                 WHERE r.run_id=? AND r.tenant_id IS NOT DISTINCT FROM ? AND r.status='RUNNING'
                 ORDER BY o.projection_sequence
                 LIMIT ?
                """;
        List<ReplayEvent> events = new ArrayList<>();
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, runId);
            s.setObject(2, tenantId);
            s.setInt(3, limit);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) events.add(new ReplayEvent(r.getLong(1), r.getObject(2, UUID.class),
                        r.getString(3), r.getString(4), r.getInt(5), r.getObject(6, UUID.class),
                        r.getObject(7, UUID.class), r.getLong(8), r.getString(9),
                        r.getObject(10, OffsetDateTime.class).toInstant()));
            }
            return List.copyOf(events);
        } catch (SQLException e) {
            throw failure("read historical projection rebuild page", e);
        }
    }

    @Override
    public boolean advance(UUID tenantId, UUID runId, long expectedCursor, long nextCursor, Instant now) {
        if (nextCursor <= expectedCursor) throw new IllegalArgumentException("next cursor must advance");
        String sql = """
                UPDATE historical_relationship_projection_rebuild_run
                   SET cursor_sequence=?, updated_at=?
                 WHERE run_id=? AND tenant_id IS NOT DISTINCT FROM ?
                   AND status='RUNNING' AND cursor_sequence=? AND high_water_sequence>=?
                """;
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setLong(1, nextCursor);
            s.setTimestamp(2, Timestamp.from(now));
            s.setObject(3, runId);
            s.setObject(4, tenantId);
            s.setLong(5, expectedCursor);
            s.setLong(6, nextCursor);
            return s.executeUpdate() == 1;
        } catch (SQLException e) {
            throw failure("advance historical projection rebuild checkpoint", e);
        }
    }

    @Override
    public boolean complete(UUID tenantId, UUID runId, long expectedCursor, Instant now) {
        String sql = """
                UPDATE historical_relationship_projection_rebuild_run r
                   SET status='COMPLETED', updated_at=?, completed_at=?
                 WHERE r.run_id=? AND r.tenant_id IS NOT DISTINCT FROM ?
                   AND r.status='RUNNING' AND r.cursor_sequence=?
                   AND NOT EXISTS (
                       SELECT 1 FROM historical_relationship_projection_outbox o
                        WHERE o.tenant_id IS NOT DISTINCT FROM r.tenant_id
                          AND o.projection_sequence > r.cursor_sequence
                          AND o.projection_sequence <= r.high_water_sequence
                   )
                """;
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setTimestamp(1, Timestamp.from(now));
            s.setTimestamp(2, Timestamp.from(now));
            s.setObject(3, runId);
            s.setObject(4, tenantId);
            s.setLong(5, expectedCursor);
            return s.executeUpdate() == 1;
        } catch (SQLException e) {
            throw failure("complete historical projection rebuild", e);
        }
    }

    private static RebuildRun mapRun(ResultSet r) throws SQLException {
        return new RebuildRun(r.getObject("run_id", UUID.class), r.getObject("tenant_id", UUID.class),
                r.getLong("high_water_sequence"), r.getLong("cursor_sequence"),
                RebuildRun.Status.valueOf(r.getString("status")),
                r.getObject("created_at", OffsetDateTime.class).toInstant(),
                r.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void rollback(Connection c, Throwable failure) {
        try { c.rollback(); } catch (SQLException e) { failure.addSuppressed(e); }
    }

    private static IllegalStateException failure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
