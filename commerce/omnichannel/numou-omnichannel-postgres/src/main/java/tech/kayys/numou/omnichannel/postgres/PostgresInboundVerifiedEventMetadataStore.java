package tech.kayys.numou.omnichannel.postgres;

import tech.kayys.numou.omnichannel.core.InboundEventKey;
import tech.kayys.numou.omnichannel.core.InboundPayloadReference;
import tech.kayys.numou.omnichannel.core.InboundVerifiedEventMetadata;
import tech.kayys.numou.omnichannel.core.InboundVerifiedEventMetadataStore;
import tech.kayys.numou.omnichannel.core.InboundWorkItem;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL metadata lookup fenced by the current PROCESSING lease token. */
public final class PostgresInboundVerifiedEventMetadataStore implements InboundVerifiedEventMetadataStore {
    private static final String FIND_SQL = """
            SELECT payload_reference, verified_at, provider_occurred_at
              FROM numou_omnichannel_inbound_event
             WHERE tenant_id = ? AND provider_id = ? AND provider_account_id = ?
               AND external_event_id = ? AND state = 'PROCESSING'
               AND lease_token = ?::uuid
               AND payload_reference IS NOT NULL AND verified_at IS NOT NULL
            """;

    private final DataSource dataSource;

    public PostgresInboundVerifiedEventMetadataStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<InboundVerifiedEventMetadata> findForLease(InboundWorkItem item) {
        Objects.requireNonNull(item, "item must not be null");
        if (item.leaseToken().isEmpty()) {
            throw new IllegalArgumentException("a leased work item is required");
        }
        InboundEventKey key = item.key();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_SQL)) {
            statement.setString(1, key.tenantId().toString());
            statement.setString(2, key.providerId());
            statement.setString(3, key.providerAccountId());
            statement.setString(4, key.externalEventId());
            statement.setString(5, item.leaseToken().orElseThrow());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp verifiedAt = rs.getTimestamp("verified_at");
                Timestamp occurredAt = rs.getTimestamp("provider_occurred_at");
                return Optional.of(new InboundVerifiedEventMetadata(
                        new InboundPayloadReference(rs.getString("payload_reference")),
                        verifiedAt.toInstant(),
                        Optional.ofNullable(occurredAt).map(Timestamp::toInstant)));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load leased verified-event metadata", exception);
        }
    }
}
