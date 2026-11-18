package tech.kayys.numou.workspace.persistence.jdbc;

import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxHealthStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Aggregate-only PostgreSQL health reader; never selects recipient or comment payloads. */
public final class JdbcWorkspaceMentionOutboxHealthStore implements WorkspaceMentionOutboxHealthStore {
    private static final String SQL = """
            SELECT
              count(*) FILTER (WHERE status='PENDING') AS pending,
              count(*) FILTER (WHERE status='PENDING' AND available_at<=?) AS due,
              count(*) FILTER (WHERE status='LEASED') AS leased,
              count(*) FILTER (WHERE status='LEASED' AND lease_until<=?) AS expired_leases,
              count(*) FILTER (WHERE status='DEAD') AS dead,
              min(created_at) FILTER (WHERE status='PENDING') AS oldest_pending_at
            FROM workspace_comment_mention_outbox
            WHERE tenant_id=?
            """;

    private final DataSource dataSource;

    public JdbcWorkspaceMentionOutboxHealthStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Snapshot snapshot(UUID tenantId, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setObject(3, tenantId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("outbox aggregate returned no row");
                Timestamp oldest = result.getTimestamp("oldest_pending_at");
                return new Snapshot(result.getLong("pending"), result.getLong("due"),
                        result.getLong("leased"), result.getLong("expired_leases"),
                        result.getLong("dead"), oldest == null ? null : oldest.toInstant());
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to read workspace mention outbox health", failure);
        }
    }
}
