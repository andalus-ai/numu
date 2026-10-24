package tech.kayys.numou.omnichannel.postgres;

import tech.kayys.numou.omnichannel.core.InboundEventKey;
import tech.kayys.numou.omnichannel.core.InboundEventVerificationStore;
import tech.kayys.numou.omnichannel.core.InboundPayloadReference;
import tech.kayys.numou.omnichannel.core.InboundVerificationDisposition;
import tech.kayys.numou.omnichannel.core.InboundVerificationResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** JDBC adapter for the atomic, digest-checked RECEIVED-to-VERIFIED transition. */
public final class PostgresInboundEventVerificationStore implements InboundEventVerificationStore {

    private static final String VERIFY_SQL = """
            WITH updated AS (
                UPDATE numou_omnichannel_inbound_event
                   SET state = 'VERIFIED', payload_reference = ?, verified_at = ?, updated_at = ?
                 WHERE tenant_id = ? AND provider_id = ? AND provider_account_id = ?
                   AND external_event_id = ? AND state = 'RECEIVED' AND payload_sha256 = ?
                RETURNING payload_reference
            )
            SELECT 'VERIFIED' AS disposition, payload_reference FROM updated
            UNION ALL
            SELECT CASE
                     WHEN e.tenant_id IS NULL THEN 'NOT_FOUND'
                     WHEN lower(e.payload_sha256) <> lower(?) THEN 'DIGEST_MISMATCH'
                     WHEN e.state = 'VERIFIED' AND e.payload_reference = ? THEN 'ALREADY_VERIFIED'
                     WHEN e.state <> 'RECEIVED' THEN 'INVALID_STATE'
                     ELSE 'CONCURRENT_CHANGE'
                   END AS disposition,
                   e.payload_reference
              FROM (SELECT 1) seed
              LEFT JOIN numou_omnichannel_inbound_event e
                ON e.tenant_id = ? AND e.provider_id = ? AND e.provider_account_id = ?
               AND e.external_event_id = ?
             WHERE NOT EXISTS (SELECT 1 FROM updated)
            """;

    private final DataSource dataSource;

    public PostgresInboundEventVerificationStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public InboundVerificationResult markVerified(
            InboundEventKey key,
            String expectedSha256,
            InboundPayloadReference payloadReference,
            Instant verifiedAt) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(payloadReference, "payloadReference must not be null");
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        String digest = normalizeDigest(expectedSha256);

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "PostgresInboundEventVerificationStore requires auto-commit connections");
            }
            try (PreparedStatement statement = connection.prepareStatement(VERIFY_SQL)) {
                statement.setString(1, payloadReference.value());
                statement.setTimestamp(2, Timestamp.from(verifiedAt));
                statement.setTimestamp(3, Timestamp.from(verifiedAt));
                bindKey(statement, 4, key);
                statement.setString(8, digest);
                statement.setString(9, digest);
                statement.setString(10, payloadReference.value());
                bindKey(statement, 11, key);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Verification transition returned no disposition");
                    }
                    InboundVerificationDisposition disposition =
                            InboundVerificationDisposition.valueOf(rs.getString("disposition"));
                    String storedReference = rs.getString("payload_reference");
                    Optional<InboundPayloadReference> reference =
                            disposition == InboundVerificationDisposition.VERIFIED
                                    || disposition == InboundVerificationDisposition.ALREADY_VERIFIED
                                    ? Optional.of(new InboundPayloadReference(storedReference))
                                    : Optional.empty();
                    return new InboundVerificationResult(disposition, reference);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to persist inbound event verification state", exception);
        }
    }

    private static String normalizeDigest(String value) {
        Objects.requireNonNull(value, "expectedSha256 must not be null");
        String digest = value.trim().toLowerCase(Locale.ROOT);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedSha256 must be a 64-character SHA-256 hex digest");
        }
        return digest;
    }

    private static void bindKey(PreparedStatement statement, int start, InboundEventKey key) throws SQLException {
        statement.setString(start, key.tenantId().toString());
        statement.setString(start + 1, key.providerId());
        statement.setString(start + 2, key.providerAccountId());
        statement.setString(start + 3, key.externalEventId());
    }
}
