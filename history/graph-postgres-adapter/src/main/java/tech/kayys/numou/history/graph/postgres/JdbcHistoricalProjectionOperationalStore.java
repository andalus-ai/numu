package tech.kayys.numou.history.graph.postgres;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOperationalStore.OperationalSnapshot;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Read-only PostgreSQL health snapshot for outbox delivery and durable rebuilds. */
public final class JdbcHistoricalProjectionOperationalStore implements HistoricalProjectionOperationalStore {
    private final DataSource source;
    public JdbcHistoricalProjectionOperationalStore(DataSource source) {
        this.source=Objects.requireNonNull(source,"source");
    }

    @Override public OperationalSnapshot snapshot(UUID tenantId, Instant now) {
        Objects.requireNonNull(now,"now");
        String outbox="""
            SELECT
              COUNT(*) FILTER (WHERE published_at IS NULL AND dead_lettered_at IS NULL
                AND (lease_until IS NULL OR lease_until<=?)) AS pending,
              COUNT(*) FILTER (WHERE published_at IS NULL AND dead_lettered_at IS NULL
                AND lease_until>?) AS leased,
              COUNT(*) FILTER (WHERE dead_lettered_at IS NOT NULL) AS dead,
              COALESCE(EXTRACT(EPOCH FROM (? - MIN(created_at) FILTER
                (WHERE published_at IS NULL AND dead_lettered_at IS NULL
                 AND (lease_until IS NULL OR lease_until<=?)))),0)::bigint AS oldest_pending_age,
              COALESCE(EXTRACT(EPOCH FROM (? - MIN(created_at) FILTER
                (WHERE published_at IS NULL AND dead_lettered_at IS NULL))),0)::bigint AS max_unpublished_age
            FROM historical_relationship_projection_outbox
            WHERE tenant_id IS NOT DISTINCT FROM ?
            """;
        String rebuild="""
            SELECT COUNT(*) FILTER (WHERE r.status='RUNNING') AS running,
                   COALESCE(SUM((SELECT COUNT(*) FROM historical_relationship_projection_outbox o
                     WHERE o.tenant_id IS NOT DISTINCT FROM r.tenant_id
                       AND o.projection_sequence > r.cursor_sequence
                       AND o.projection_sequence <= r.high_water_sequence)
                     ) FILTER (WHERE r.status='RUNNING'),0)::bigint AS remaining,
                   COALESCE(EXTRACT(EPOCH FROM (? - MIN(r.created_at)
                     FILTER (WHERE r.status='RUNNING'))),0)::bigint AS oldest_age
              FROM historical_relationship_projection_rebuild_run r
             WHERE tenant_id IS NOT DISTINCT FROM ?
            """;
        try(Connection c=source.getConnection()) {
            c.setAutoCommit(false);
            if (tenantId != null) {
                try(PreparedStatement scope=c.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
                    scope.setString(1,tenantId.toString());
                    scope.execute();
                }
            }
            long pending,leased,dead,oldestPending,maxUnpublished,running,remaining,oldestRebuild;
            try(PreparedStatement s=c.prepareStatement(outbox)) {
                s.setTimestamp(1,Timestamp.from(now)); s.setTimestamp(2,Timestamp.from(now));
                s.setTimestamp(3,Timestamp.from(now)); s.setTimestamp(4,Timestamp.from(now));
                s.setTimestamp(5,Timestamp.from(now)); s.setObject(6,tenantId);
                try(ResultSet r=s.executeQuery()) {
                    if(!r.next()) throw new SQLException("outbox metrics query returned no row");
                    pending=r.getLong("pending"); leased=r.getLong("leased"); dead=r.getLong("dead");
                    oldestPending=r.getLong("oldest_pending_age"); maxUnpublished=r.getLong("max_unpublished_age");
                }
            }
            try(PreparedStatement s=c.prepareStatement(rebuild)) {
                s.setTimestamp(1,Timestamp.from(now)); s.setObject(2,tenantId);
                try(ResultSet r=s.executeQuery()) {
                    if(!r.next()) throw new SQLException("rebuild metrics query returned no row");
                    running=r.getLong("running"); remaining=r.getLong("remaining"); oldestRebuild=r.getLong("oldest_age");
                }
            }
            c.commit();
            return new OperationalSnapshot(tenantId,pending,leased,dead,oldestPending,maxUnpublished,
                    running,remaining,oldestRebuild,now);
        } catch(SQLException e) {
            throw new IllegalStateException("Unable to collect historical projection operational metrics",e);
        }
    }
}
