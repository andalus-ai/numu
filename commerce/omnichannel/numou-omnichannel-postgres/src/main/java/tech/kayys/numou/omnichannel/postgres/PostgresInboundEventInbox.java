package tech.kayys.numou.omnichannel.postgres;

import tech.kayys.numou.omnichannel.core.InboundEventEnvelope;
import tech.kayys.numou.omnichannel.core.InboundEventInbox;
import tech.kayys.numou.omnichannel.core.InboundEventKey;
import tech.kayys.numou.omnichannel.core.InboxClaimDisposition;
import tech.kayys.numou.omnichannel.core.InboxClaimResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL implementation of atomic inbound-event claiming.
 *
 * <p>The database uniqueness constraint is the concurrency boundary. This
 * adapter persists metadata and a payload digest only; it does not persist
 * raw webhook bodies or dispatch business commands.</p>
 *
 * <p>The configured DataSource must return connections in auto-commit mode.
 * Each claim is one atomic PostgreSQL INSERT ... ON CONFLICT statement, so a
 * successful ACCEPTED result is committed before it is returned.</p>
 */
public final class PostgresInboundEventInbox implements InboundEventInbox {

    private static final String INSERT_SQL = """
            INSERT INTO numou_omnichannel_inbound_event (
                tenant_id, provider_id, provider_account_id, external_event_id,
                event_type, payload_sha256, channel_type, branch_id,
                correlation_id, received_at, provider_occurred_at, state
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED')
            ON CONFLICT (tenant_id, provider_id, provider_account_id, external_event_id)
            DO NOTHING
            """;

    private static final String SELECT_DIGEST_SQL = """
            SELECT payload_sha256
              FROM numou_omnichannel_inbound_event
             WHERE tenant_id = ?
               AND provider_id = ?
               AND provider_account_id = ?
               AND external_event_id = ?
            """;

    private final DataSource dataSource;

    public PostgresInboundEventInbox(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public InboxClaimResult claim(String providerId, InboundEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        InboundEventKey key = InboundEventKey.from(envelope, providerId);

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "PostgresInboundEventInbox requires an auto-commit DataSource; "
                                + "claim must commit independently before dispatch");
            }

            int inserted;
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                statement.setString(1, key.tenantId().toString());
                statement.setString(2, key.providerId());
                statement.setString(3, key.providerAccountId());
                statement.setString(4, key.externalEventId());
                statement.setString(5, envelope.eventType());
                statement.setString(6, envelope.payloadSha256());
                statement.setString(7, envelope.context().channelType().name());
                statement.setString(8, envelope.context().branchId().orElse(null));
                statement.setString(9, envelope.context().correlationId());
                statement.setTimestamp(10, Timestamp.from(envelope.receivedAt()));
                setOptionalTimestamp(statement, 11, envelope.providerOccurredAt());
                inserted = statement.executeUpdate();
            }

            if (inserted == 1) {
                return new InboxClaimResult(key, InboxClaimDisposition.ACCEPTED, Optional.empty());
            }

            String existingDigest = findExistingDigest(connection, key);
            if (existingDigest == null) {
                // Under READ COMMITTED, ON CONFLICT waits for the conflicting
                // transaction. A missing row indicates deletion or a schema /
                // transaction isolation mismatch and must not be treated as a
                // successful claim.
                throw new IllegalStateException(
                        "Conflicting inbox row disappeared before its digest could be read");
            }

            InboxClaimDisposition disposition = existingDigest.equalsIgnoreCase(envelope.payloadSha256())
                    ? InboxClaimDisposition.DUPLICATE
                    : InboxClaimDisposition.PAYLOAD_CONFLICT;
            return new InboxClaimResult(key, disposition, Optional.of(existingDigest));
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to claim inbound event in PostgreSQL inbox", exception);
        }
    }

    private static String findExistingDigest(Connection connection, InboundEventKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_DIGEST_SQL)) {
            statement.setString(1, key.tenantId().toString());
            statement.setString(2, key.providerId());
            statement.setString(3, key.providerAccountId());
            statement.setString(4, key.externalEventId());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static void setOptionalTimestamp(
            PreparedStatement statement, int index, Optional<Instant> value) throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setTimestamp(index, null);
        }
    }
}
