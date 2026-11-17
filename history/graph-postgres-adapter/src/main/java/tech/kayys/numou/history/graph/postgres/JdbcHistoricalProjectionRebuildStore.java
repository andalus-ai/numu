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
        Objects.requireNonNull(now, "now");
        UUID runId = UUID.randomUUID();
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                setTenantScope(c, tenantId);
                // SHARE conflicts with outbox INSERT's ROW EXCLUSIVE lock. Once acquired,
                // all earlier event transactions have committed; later inserts receive a
                // sequence above this run's immutable high-water mark.
                try (Statement lock = c.createStatement()) {
                    lock.execute("LOCK TABLE historical_relationship_projection_outbox IN SHARE MODE");
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
            throw failure("start historical projection rebuild", e);
        }
    }

    @Override
    public Optional<RebuildRun> find(UUID tenantId, UUID runId) {
        Objects.requireNonNull(runId, "runId");
        String sql = "SELECT * FROM historical_relationship_projection_rebuild_run WHERE run_id=? AND tenant_id IS NOT DISTINCT FROM ?";
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                setTenantScope(c, tenantId);
                Optional<RebuildRun> result;
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, runId);
                    s.setObject(2, tenantId);
                    try (ResultSet r = s.executeQuery()) {
                        result = r.next() ? Optional.of(mapRun(r)) : Optional.empty();
                    }
                }
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                throw e;
            }
        } catch (SQLException e) {
            throw failure("load historical projection rebuild", e);
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
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                setTenantScope(c, tenantId);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, runId);
                    s.setObject(2, tenantId);
                    s.setInt(3, limit);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) events.add(new ReplayEvent(r.getLong(1), r.getObject(2, UUID.class),
                                r.getString(3), r.getString(4), r.getInt(5), r.getObject(6, UUID.class),
                                r.getObject(7, UUID.class), r.getLong(8), r.getString(9),
                                r.getObject(10, OffsetDateTime.class).toInstant()));
                    }
                }
                c.commit();
                return List.copyOf(events);
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                throw e;
            }
        } catch (SQLException e) {
            throw failure("read historical projection rebuild page", e);
        }
    }

    @Override
    @Deprecated
    public boolean advance(UUID tenantId, UUID runId, long expectedCursor, long nextCursor, Instant now) {
        throw new UnsupportedOperationException("use advanceFenced with the observed lifecycle version");
    }

    @Override
    public boolean advanceFenced(UUID tenantId, UUID runId, long expectedCursor, long nextCursor,
                                 long expectedLifecycleVersion, Instant now) {
        if (nextCursor <= expectedCursor) throw new IllegalArgumentException("next cursor must advance");
        String sql = """
                UPDATE historical_relationship_projection_rebuild_run
                   SET cursor_sequence=?, updated_at=?, lifecycle_version=lifecycle_version+1
                 WHERE run_id=? AND tenant_id IS NOT DISTINCT FROM ?
                   AND status='RUNNING' AND cursor_sequence=? AND lifecycle_version=?
                   AND high_water_sequence>=?
                """;
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setLong(1, nextCursor);
            s.setTimestamp(2, Timestamp.from(now));
            s.setObject(3, runId);
            s.setObject(4, tenantId);
            s.setLong(5, expectedCursor);
            s.setLong(6, expectedLifecycleVersion);
            s.setLong(7, nextCursor);
            return s.executeUpdate() == 1;
        } catch (SQLException e) {
            throw failure("advance fenced historical projection rebuild checkpoint", e);
        }
    }

    @Override
    public boolean transition(UUID tenantId, UUID runId, UUID actorId, Action action,
                              long expectedLifecycleVersion, RebuildRun.Status expectedStatus,
                              RebuildRun.Status targetStatus, String detail, Instant now) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(expectedStatus, "expectedStatus");
        Objects.requireNonNull(targetStatus, "targetStatus");
        Objects.requireNonNull(now, "now");
        validateTransition(action, expectedStatus, targetStatus);
        String safeDetail = Objects.toString(detail, "").replaceAll("[\\r\\n\\t]+", " ");
        if (safeDetail.length() > 1000) safeDetail = safeDetail.substring(0, 1000);

        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (tenantId != null) {
                    try (PreparedStatement scope = c.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
                        scope.setString(1, tenantId.toString());
                        scope.execute();
                    }
                }
                String update = """
                    UPDATE historical_relationship_projection_rebuild_run
                       SET status=?, updated_at=?, lifecycle_version=lifecycle_version+1,
                           paused_at=CASE WHEN ?='PAUSED' THEN ? ELSE paused_at END,
                           cancelled_at=CASE WHEN ?='CANCELLED' THEN ? ELSE cancelled_at END,
                           failed_at=CASE WHEN ?='FAILED' THEN ? ELSE failed_at END,
                           failure_detail=CASE WHEN ?='FAILED' THEN ? ELSE '' END
                     WHERE run_id=? AND tenant_id IS NOT DISTINCT FROM ?
                       AND status=? AND lifecycle_version=?
                    """;
                int changed;
                try (PreparedStatement s = c.prepareStatement(update)) {
                    int i = 1;
                    s.setString(i++, targetStatus.name());
                    s.setTimestamp(i++, Timestamp.from(now));
                    s.setString(i++, targetStatus.name()); s.setTimestamp(i++, Timestamp.from(now));
                    s.setString(i++, targetStatus.name()); s.setTimestamp(i++, Timestamp.from(now));
                    s.setString(i++, targetStatus.name()); s.setTimestamp(i++, Timestamp.from(now));
                    s.setString(i++, targetStatus.name()); s.setString(i++, safeDetail);
                    s.setObject(i++, runId); s.setObject(i++, tenantId);
                    s.setString(i++, expectedStatus.name()); s.setLong(i, expectedLifecycleVersion);
                    changed = s.executeUpdate();
                }
                if (changed != 1) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement audit = c.prepareStatement("""
                    INSERT INTO historical_projection_rebuild_audit
                      (audit_id, run_id, tenant_id, actor_id, action, previous_status,
                       resulting_status, lifecycle_version, detail, occurred_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    audit.setObject(1, UUID.randomUUID());
                    audit.setObject(2, runId);
                    audit.setObject(3, tenantId);
                    audit.setObject(4, actorId);
                    audit.setString(5, action.name());
                    audit.setString(6, expectedStatus.name());
                    audit.setString(7, targetStatus.name());
                    audit.setLong(8, expectedLifecycleVersion + 1);
                    audit.setString(9, safeDetail);
                    audit.setTimestamp(10, Timestamp.from(now));
                    audit.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                throw e;
            }
        } catch (SQLException e) {
            throw failure("transition historical projection rebuild lifecycle", e);
        }
    }

    private static void validateTransition(Action action, RebuildRun.Status from, RebuildRun.Status to) {
        boolean valid = switch (action) {
            case PAUSE -> from == RebuildRun.Status.RUNNING && to == RebuildRun.Status.PAUSED;
            case RESUME -> from == RebuildRun.Status.PAUSED && to == RebuildRun.Status.RUNNING;
            case CANCEL -> (from == RebuildRun.Status.RUNNING || from == RebuildRun.Status.PAUSED
                    || from == RebuildRun.Status.FAILED) && to == RebuildRun.Status.CANCELLED;
            case RETRY -> from == RebuildRun.Status.FAILED && to == RebuildRun.Status.RUNNING;
            case FAIL -> from == RebuildRun.Status.RUNNING && to == RebuildRun.Status.FAILED;
        };
        if (!valid) throw new IllegalArgumentException("invalid rebuild lifecycle transition: " + action + " " + from + " -> " + to);
    }

    @Override
    @Deprecated
    public boolean complete(UUID tenantId, UUID runId, long expectedCursor, Instant now) {
        throw new UnsupportedOperationException("use completeFenced with the observed lifecycle version");
    }

    @Override
    public boolean completeFenced(UUID tenantId, UUID runId, long expectedCursor,
                                   long expectedLifecycleVersion, Instant now) {
        String sql = """
                UPDATE historical_relationship_projection_rebuild_run r
                   SET status='COMPLETED', updated_at=?, completed_at=?,
                       lifecycle_version=lifecycle_version+1
                 WHERE r.run_id=? AND r.tenant_id IS NOT DISTINCT FROM ?
                   AND r.status='RUNNING' AND r.cursor_sequence=? AND r.lifecycle_version=?
                   AND NOT EXISTS (
                       SELECT 1 FROM historical_relationship_projection_outbox o
                        WHERE o.tenant_id IS NOT DISTINCT FROM r.tenant_id
                          AND o.projection_sequence > r.cursor_sequence
                          AND o.projection_sequence <= r.high_water_sequence
                   )
                """;
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                setTenantScope(c, tenantId);
                boolean completed;
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setTimestamp(1, Timestamp.from(now));
                    s.setTimestamp(2, Timestamp.from(now));
                    s.setObject(3, runId);
                    s.setObject(4, tenantId);
                    s.setLong(5, expectedCursor);
                    s.setLong(6, expectedLifecycleVersion);
                    completed = s.executeUpdate() == 1;
                }
                c.commit();
                return completed;
            } catch (SQLException | RuntimeException e) {
                rollback(c, e);
                throw e;
            }
        } catch (SQLException e) {
            throw failure("complete fenced historical projection rebuild", e);
        }
    }

    private static RebuildRun mapRun(ResultSet r) throws SQLException {
        return new RebuildRun(r.getObject("run_id", UUID.class), r.getObject("tenant_id", UUID.class),
                r.getLong("high_water_sequence"), r.getLong("cursor_sequence"),
                RebuildRun.Status.valueOf(r.getString("status")),
                r.getObject("created_at", OffsetDateTime.class).toInstant(),
                r.getObject("updated_at", OffsetDateTime.class).toInstant(),
                r.getLong("lifecycle_version"), r.getString("failure_detail"));
    }

    private static void setTenantScope(Connection c, UUID tenantId) throws SQLException {
        if (tenantId == null) return;
        try (PreparedStatement scope = c.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
            scope.setString(1, tenantId.toString());
            scope.execute();
        }
    }

    private static void rollback(Connection c, Throwable failure) {
        try { c.rollback(); } catch (SQLException e) { failure.addSuppressed(e); }
    }

    private static IllegalStateException failure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
