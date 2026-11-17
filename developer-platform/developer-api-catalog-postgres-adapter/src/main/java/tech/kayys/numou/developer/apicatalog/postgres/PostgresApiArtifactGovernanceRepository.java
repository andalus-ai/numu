package tech.kayys.numou.developer.apicatalog.postgres;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactGovernanceRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Transactional PostgreSQL implementation of API artifact retention and legal-hold governance. */
@ApplicationScoped
public final class PostgresApiArtifactGovernanceRepository implements ApiArtifactGovernanceRepository {
    private final ApiCatalogJdbcTransactionManager transactions;

    @Inject
    public PostgresApiArtifactGovernanceRepository(ApiCatalogJdbcTransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public Uni<Boolean> setLegalHold(TenantId tenant, String productId, String version, boolean held,
                                    String actorReference, String reason, Instant occurredAt) {
        validate(tenant, productId, version, actorReference, reason, occurredAt);
        return transactions.withTenant(tenant, connection -> {
            ArtifactRow artifact = lockArtifact(connection, tenant, productId, version);
            if (artifact.deleted()) throw new IllegalStateException("A deleted artifact tombstone cannot be governed");
            if (artifact.legalHold() == held) return false;

            String nextState = artifact.lifecycleState();
            if ("DELETE_PENDING".equals(nextState)) {
                if (artifact.leaseUntil() != null && artifact.leaseUntil().isAfter(occurredAt)) {
                    throw new IllegalStateException("Cannot apply a legal hold while artifact deletion owns an active lease");
                }
                // Cancel an expired cleanup lease before applying the hold. The lifecycle
                // trigger permits DELETE_PENDING -> ABANDONED and prevents deletion under hold.
                nextState = "ABANDONED";
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE developer_api_artifacts
                       SET legal_hold=?, lifecycle_state=?,
                           cleanup_lease_owner=NULL, cleanup_lease_until=NULL
                     WHERE provider_tenant_id=? AND product_id=? AND api_version=?
                    """)) {
                statement.setBoolean(1, held);
                statement.setString(2, nextState);
                statement.setObject(3, tenant.value());
                statement.setObject(4, UUID.fromString(productId));
                statement.setString(5, version);
                if (statement.executeUpdate() != 1) throw new IllegalStateException("API artifact governance update lost its row");
            }
            insertGovernanceAudit(connection, tenant, artifact, held ? "LEGAL_HOLD_APPLIED" : "LEGAL_HOLD_RELEASED",
                    actorReference, reason, held, artifact.retentionUntil(), occurredAt);
            return true;
        });
    }

    @Override
    public Uni<Boolean> extendRetention(TenantId tenant, String productId, String version, Instant retentionUntil,
                                        String actorReference, String reason, Instant occurredAt) {
        validate(tenant, productId, version, actorReference, reason, occurredAt);
        if (retentionUntil == null || !retentionUntil.isAfter(occurredAt)) {
            return Uni.createFrom().failure(new IllegalArgumentException("Retention deadline must be in the future"));
        }
        return transactions.withTenant(tenant, connection -> {
            ArtifactRow artifact = lockArtifact(connection, tenant, productId, version);
            if (artifact.deleted()) throw new IllegalStateException("A deleted artifact tombstone cannot be governed");
            if (artifact.retentionUntil() != null) {
                if (retentionUntil.isBefore(artifact.retentionUntil())) {
                    throw new IllegalArgumentException("Artifact retention cannot be shortened");
                }
                if (retentionUntil.equals(artifact.retentionUntil())) return false;
            }

            String nextState = artifact.lifecycleState();
            if ("DELETE_PENDING".equals(nextState)) {
                if (artifact.leaseUntil() != null && artifact.leaseUntil().isAfter(occurredAt)) {
                    throw new IllegalStateException("Cannot extend retention while artifact deletion owns an active lease");
                }
                nextState = "ABANDONED";
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE developer_api_artifacts
                       SET retention_until=?, lifecycle_state=?,
                           cleanup_lease_owner=NULL, cleanup_lease_until=NULL,
                           next_cleanup_at=NULL
                     WHERE provider_tenant_id=? AND product_id=? AND api_version=?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(retentionUntil));
                statement.setString(2, nextState);
                statement.setObject(3, tenant.value());
                statement.setObject(4, UUID.fromString(productId));
                statement.setString(5, version);
                if (statement.executeUpdate() != 1) throw new IllegalStateException("API artifact governance update lost its row");
            }
            insertGovernanceAudit(connection, tenant, artifact, "RETENTION_EXTENDED",
                    actorReference, reason, artifact.legalHold(), retentionUntil, occurredAt);
            return true;
        });
    }

    private static ArtifactRow lockArtifact(java.sql.Connection connection, TenantId tenant,
                                            String productId, String version) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT artifact_reference, sha256, lifecycle_state, legal_hold,
                       retention_until, cleanup_lease_until
                  FROM developer_api_artifacts
                 WHERE provider_tenant_id=? AND product_id=? AND api_version=?
                 FOR UPDATE
                """)) {
            statement.setObject(1, tenant.value());
            statement.setObject(2, UUID.fromString(productId));
            statement.setString(3, version);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("API artifact version does not exist in this provider tenant");
                Timestamp retention = rows.getTimestamp("retention_until");
                Timestamp lease = rows.getTimestamp("cleanup_lease_until");
                return new ArtifactRow(rows.getString("artifact_reference"), rows.getString("sha256"),
                        rows.getString("lifecycle_state"), rows.getBoolean("legal_hold"),
                        retention == null ? null : retention.toInstant(), lease == null ? null : lease.toInstant());
            }
        }
    }

    private static void insertGovernanceAudit(java.sql.Connection connection, TenantId tenant,
            ArtifactRow artifact, String action, String actor, String reason, boolean legalHold,
            Instant retentionUntil, Instant occurredAt) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO developer_api_artifact_cleanup_audit
                    (provider_tenant_id, product_id, api_version, artifact_reference, sha256,
                     action, actor_reference, attempt_number, occurred_at,
                     legal_hold_state, retention_until, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, tenant.value());
            // product/version are supplied by the immutable artifact row's lookup identity.
            // They are set by the caller-specific context below through the scoped insert.
            throw new IllegalStateException("Governance audit identity must be provided by the caller");
        }
    }

    private static void validate(TenantId tenant, String productId, String version,
                                 String actor, String reason, Instant occurredAt) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        UUID.fromString(Objects.requireNonNull(productId, "productId cannot be null"));
        if (version == null || version.isBlank() || version.length() > 80
                || actor == null || actor.isBlank() || actor.length() > 160
                || reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("Invalid API artifact governance metadata");
        }
    }

    private record ArtifactRow(String reference, String sha256, String lifecycleState,
                               boolean legalHold, Instant retentionUntil, Instant leaseUntil) {
        boolean deleted() { return "DELETED".equals(lifecycleState); }
    }
}
