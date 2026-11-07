package tech.kayys.numou.history.graph.postgres;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionReconciliationStore;
import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Read-only latest-event queries over the immutable PostgreSQL projection outbox. */
public final class JdbcHistoricalProjectionReconciliationStore implements HistoricalProjectionReconciliationStore {
    private final DataSource source;
    public JdbcHistoricalProjectionReconciliationStore(DataSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public List<ClaimedProjectionEvent> latestPage(UUID tenantId, UUID afterId, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        String sql = """
                SELECT event_id, event_key, event_type, schema_version, tenant_id,
                       aggregate_id, aggregate_revision, payload::text, occurred_at
                  FROM (
                    SELECT DISTINCT ON (aggregate_id) *
                      FROM historical_relationship_projection_outbox
                     WHERE tenant_id IS NOT DISTINCT FROM ?
                     ORDER BY aggregate_id, aggregate_revision DESC
                  ) latest
                 WHERE (CAST(? AS UUID) IS NULL OR aggregate_id > CAST(? AS UUID))
                 ORDER BY aggregate_id
                 LIMIT ?
                """;
        List<ClaimedProjectionEvent> events = new ArrayList<>();
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setObject(2, afterId);
            s.setObject(3, afterId);
            s.setInt(4, limit);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    String eventKey = r.getString(2);
                    events.add(new ClaimedProjectionEvent(r.getObject(1, UUID.class), eventKey,
                            r.getString(3), r.getInt(4), r.getObject(5, UUID.class),
                            r.getObject(6, UUID.class), r.getLong(7), r.getString(8),
                            r.getObject(9, OffsetDateTime.class).toInstant(), 0, 0,
                            UUID.nameUUIDFromBytes(eventKey.getBytes(StandardCharsets.UTF_8)), Instant.MAX));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read canonical historical projection page", e);
        }
        return List.copyOf(events);
    }

    @Override
    public boolean exists(UUID tenantId, UUID relationshipId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM historical_relationship_revision WHERE tenant_id IS NOT DISTINCT FROM ? AND relationship_id = ?)";
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement setting = c.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
                    setting.setString(1, tenantId == null ? "" : tenantId.toString());
                    setting.execute();
                }
                boolean found;
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, tenantId);
                    s.setObject(2, relationshipId);
                    try (ResultSet r = s.executeQuery()) { r.next(); found = r.getBoolean(1); }
                }
                c.commit();
                return found;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to check canonical historical relationship", e);
        }
    }
}
