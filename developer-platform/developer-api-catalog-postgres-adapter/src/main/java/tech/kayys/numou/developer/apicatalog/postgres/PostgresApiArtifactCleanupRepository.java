package tech.kayys.numou.developer.apicatalog.postgres;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository.CleanupLease;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** PostgreSQL SKIP LOCKED lease implementation for tenant-scoped artifact cleanup. */
@ApplicationScoped
public final class PostgresApiArtifactCleanupRepository implements ApiArtifactCleanupRepository {
    private final ApiCatalogJdbcTransactionManager transactions;

    @Inject
    public PostgresApiArtifactCleanupRepository(ApiCatalogJdbcTransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions cannot be null");
    }

    @Override
    public Uni<List<CleanupLease>> claimDue(TenantId tenant, String workerId, Instant now,
                                             Instant leaseUntil, int limit, int maximumAttempts) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil cannot be null");
        if (workerId == null || workerId.isBlank() || workerId.length() > 160
                || !leaseUntil.isAfter(now) || limit < 1 || limit > 200 || maximumAttempts < 1) {
            return Uni.createFrom().failure(new IllegalArgumentException("Invalid artifact cleanup lease request"));
        }
        return transactions.withTenant(tenant, connection -> {
            String sql = """
                WITH due AS (
                    SELECT provider_tenant_id, product_id, api_version
                    FROM developer_api_artifacts
                    WHERE provider_tenant_id=?
                      AND lifecycle_state IN ('ABANDONED','DELETE_PENDING')
                      AND legal_hold=FALSE
                      AND retention_until IS NOT NULL AND retention_until<=?
                      AND (next_cleanup_at IS NULL OR next_cleanup_at<=?)
                      AND (cleanup_lease_until IS NULL OR cleanup_lease_until<=?)
                      AND cleanup_attempts<?
                    ORDER BY next_cleanup_at NULLS FIRST, retention_until, created_at, product_id, api_version
                    LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE developer_api_artifacts artifact
                   SET lifecycle_state='DELETE_PENDING',
                       cleanup_lease_owner=?,
                       cleanup_lease_until=?,
                       next_cleanup_at=?,
                       cleanup_last_error_code=NULL
                  FROM due
                 WHERE artifact.provider_tenant_id=due.provider_tenant_id
                   AND artifact.product_id=due.product_id
                   AND artifact.api_version=due.api_version
                RETURNING artifact.product_id, artifact.api_version, artifact.artifact_reference,
                          artifact.sha256, artifact.cleanup_attempts, artifact.cleanup_lease_until
                """;
            List<CleanupLease> leases = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenant.value());
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setTimestamp(3, Timestamp.from(now));
                statement.setTimestamp(4, Timestamp.from(now));
                statement.setInt(5, maximumAttempts);
                statement.setInt(6, limit);
                statement.setString(7, workerId);
                statement.setTimestamp(8, Timestamp.from(leaseUntil));
                statement.setTimestamp(9, Timestamp.from(now));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        leases.add(new CleanupLease(rows.getObject(1, java.util.UUID.class).toString(),
                                rows.getString(2), rows.getString(3), rows.getString(4),
                                rows.getInt(5), rows.getTimestamp(6).toInstant()));
                    }
                }
            }
            return List.copyOf(leases);
        });
    }

    @Override
    public Uni<Boolean> isLeaseCurrent(TenantId tenant, CleanupLease lease, String workerId,
                                       Instant now, int maximumAttempts) {
        Objects.requireNonNull(lease, "lease cannot be null");
        return transactions.withTenant(tenant, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT 1 FROM developer_api_artifacts
                    WHERE provider_tenant_id=? AND product_id=? AND api_version=?
                      AND artifact_reference=? AND sha256=?
                      AND lifecycle_state='DELETE_PENDING' AND legal_hold=FALSE
                      AND retention_until IS NOT NULL AND retention_until<=?
                      AND cleanup_lease_owner=? AND cleanup_lease_until>?
                      AND cleanup_attempts<?
                    """)) {
                statement.setObject(1, tenant.value());
                statement.setObject(2, java.util.UUID.fromString(lease.productId()));
                statement.setString(3, lease.version());
                statement.setString(4, lease.artifactReference());
                statement.setString(5, lease.sha256());
                statement.setTimestamp(6, Timestamp.from(now));
                statement.setString(7, workerId);
                statement.setTimestamp(8, Timestamp.from(now));
                statement.setInt(9, maximumAttempts);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }

    @Override
    public Uni<Void> markDeleted(TenantId tenant, CleanupLease lease, String workerId, Instant deletedAt) {
        return transactions.withTenant(tenant, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE developer_api_artifacts
                       SET lifecycle_state='DELETED', deleted_at=?,
                           cleanup_lease_owner=NULL, cleanup_lease_until=NULL,
                           cleanup_last_error_code=NULL
                     WHERE provider_tenant_id=? AND product_id=? AND api_version=?
                       AND artifact_reference=? AND sha256=?
                       AND lifecycle_state='DELETE_PENDING' AND legal_hold=FALSE
                       AND cleanup_lease_owner=? AND cleanup_lease_until>?
                       AND retention_until<=?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(deletedAt));
                statement.setObject(2, tenant.value());
                statement.setObject(3, java.util.UUID.fromString(lease.productId()));
                statement.setString(4, lease.version());
                statement.setString(5, lease.artifactReference());
                statement.setString(6, lease.sha256());
                statement.setString(7, workerId);
                statement.setTimestamp(8, Timestamp.from(deletedAt));
                statement.setTimestamp(9, Timestamp.from(deletedAt));
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Artifact cleanup lease was lost before tombstoning");
                }
            }
            return null;
        });
    }

    @Override
    public Uni<Void> recordFailure(TenantId tenant, CleanupLease lease, String workerId,
                                   String failureCode, Instant retryAt, int maximumAttempts) {
        return transactions.withTenant(tenant, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE developer_api_artifacts
                       SET lifecycle_state=CASE WHEN cleanup_attempts+1>=? THEN 'ABANDONED' ELSE 'DELETE_PENDING' END,
                           cleanup_attempts=cleanup_attempts+1,
                           next_cleanup_at=?, cleanup_lease_owner=NULL, cleanup_lease_until=NULL,
                           cleanup_last_error_code=?
                     WHERE provider_tenant_id=? AND product_id=? AND api_version=?
                       AND artifact_reference=? AND sha256=?
                       AND lifecycle_state='DELETE_PENDING' AND cleanup_lease_owner=?
                    """)) {
                statement.setInt(1, maximumAttempts);
                statement.setTimestamp(2, Timestamp.from(retryAt));
                statement.setString(3, failureCode == null ? "CLEANUP_FAILURE" : failureCode.substring(0, Math.min(120, failureCode.length())));
                statement.setObject(4, tenant.value());
                statement.setObject(5, java.util.UUID.fromString(lease.productId()));
                statement.setString(6, lease.version());
                statement.setString(7, lease.artifactReference());
                statement.setString(8, lease.sha256());
                statement.setString(9, workerId);
                statement.executeUpdate(); // A stale worker must not overwrite a newer lease.
            }
            return null;
        });
    }
}
