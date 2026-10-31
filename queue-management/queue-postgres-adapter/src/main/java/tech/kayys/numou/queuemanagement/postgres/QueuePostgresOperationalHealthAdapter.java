package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.queuemanagement.spi.QueueOperationalHealthPort;
import tech.kayys.numou.queuemanagement.spi.QueueOperationalSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL operational read model. It deliberately aggregates metadata only:
 * queue_outbox.payload is never selected or materialized.
 */
@ApplicationScoped
public class QueuePostgresOperationalHealthAdapter implements QueueOperationalHealthPort {
    private static final String SNAPSHOT_SQL = """
            WITH clock AS (
                SELECT CURRENT_TIMESTAMP AS captured_at
            ),
            entry_stats AS (
                SELECT
                    COUNT(*) FILTER (WHERE status = 'WAITING') AS waiting_entries,
                    COUNT(*) FILTER (WHERE status IN ('CALLED', 'SERVING', 'OFFERED', 'ACCEPTED')) AS active_entries
                FROM queue_entry
                WHERE tenant_id = ?
            ),
            outbox_stats AS (
                SELECT
                    COUNT(*) FILTER (
                        WHERE published_at IS NULL AND dead_lettered_at IS NULL
                    ) AS pending_outbox,
                    COUNT(*) FILTER (
                        WHERE published_at IS NULL AND dead_lettered_at IS NULL
                          AND available_at <= clock.captured_at
                          AND (lease_until IS NULL OR lease_until <= clock.captured_at)
                    ) AS due_outbox,
                    COUNT(*) FILTER (
                        WHERE published_at IS NULL AND dead_lettered_at IS NULL
                          AND lease_until > clock.captured_at
                    ) AS leased_outbox,
                    COUNT(*) FILTER (
                        WHERE published_at IS NULL AND dead_lettered_at IS NULL
                          AND lease_token IS NOT NULL AND lease_until <= clock.captured_at
                    ) AS expired_leases,
                    COUNT(*) FILTER (
                        WHERE published_at IS NULL AND dead_lettered_at IS NOT NULL
                    ) AS dead_lettered_outbox,
                    COALESCE(
                        FLOOR(EXTRACT(EPOCH FROM (
                            clock.captured_at - MIN(created_at) FILTER (
                                WHERE published_at IS NULL AND dead_lettered_at IS NULL
                            )
                        ))),
                        0
                    )::bigint AS oldest_pending_age_seconds
                FROM queue_outbox
                CROSS JOIN clock
                WHERE tenant_id = ?
            )
            SELECT clock.captured_at, entry_stats.waiting_entries, entry_stats.active_entries,
                   outbox_stats.pending_outbox, outbox_stats.due_outbox,
                   outbox_stats.leased_outbox, outbox_stats.expired_leases,
                   outbox_stats.dead_lettered_outbox, outbox_stats.oldest_pending_age_seconds
              FROM clock CROSS JOIN entry_stats CROSS JOIN outbox_stats
            """;

    private final AgroalDataSource dataSource;

    @Inject
    public QueuePostgresOperationalHealthAdapter(AgroalDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    @Transactional
    public QueueOperationalSnapshot snapshot(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement tenant = connection.prepareStatement(
                    "SELECT set_config('numou.tenant_id', ?, true)")) {
                tenant.setString(1, tenantId.toString());
                tenant.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement(SNAPSHOT_SQL)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, tenantId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Queue operational snapshot query returned no row");
                    }
                    Instant capturedAt = rs.getTimestamp("captured_at").toInstant();
                    return new QueueOperationalSnapshot(
                            tenantId,
                            capturedAt,
                            rs.getLong("waiting_entries"),
                            rs.getLong("active_entries"),
                            rs.getLong("pending_outbox"),
                            rs.getLong("due_outbox"),
                            rs.getLong("leased_outbox"),
                            rs.getLong("expired_leases"),
                            rs.getLong("dead_lettered_outbox"),
                            rs.getLong("oldest_pending_age_seconds"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to read queue operational snapshot", e);
        }
    }
}
