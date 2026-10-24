package tech.kayys.numou.omnichannel.postgres;
import tech.kayys.numou.omnichannel.core.InboundEventRecoveryStore;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
/** Bounded PostgreSQL recovery for inbox rows stranded in RECEIVED. */
public final class PostgresInboundEventRecoveryStore implements InboundEventRecoveryStore {
    private static final String QUARANTINE_SQL = """
            WITH stale AS (
                SELECT tenant_id, provider_id, provider_account_id, external_event_id
                  FROM numou_omnichannel_inbound_event
                 WHERE state = 'RECEIVED' AND received_at <= ? AND payload_reference IS NULL
                 ORDER BY received_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE numou_omnichannel_inbound_event e
               SET state = 'DEAD_LETTER', last_error_code = 'INGESTION_INCOMPLETE',
                   next_attempt_at = NULL, lease_until = NULL, lease_token = NULL, updated_at = ?
              FROM stale s
             WHERE e.tenant_id = s.tenant_id AND e.provider_id = s.provider_id
               AND e.provider_account_id = s.provider_account_id
               AND e.external_event_id = s.external_event_id AND e.state = 'RECEIVED'
            """;
    private final DataSource dataSource;
    public PostgresInboundEventRecoveryStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }
    @Override public int quarantineStaleReceived(Instant cutoff, Instant now, int limit) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(QUARANTINE_SQL)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, limit);
            statement.setTimestamp(3, Timestamp.from(now));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to quarantine incomplete inbound ingestions", exception);
        }
    }
}
