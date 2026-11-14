package tech.kayys.numou.developer.apicatalog.postgres;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository.CleanupLease;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository.CleanupOperationalSnapshot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL SKIP LOCKED lease and append-only audit implementation. */
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
                        CleanupLease lease = new CleanupLease(rows.getObject(1, UUID.class).toString(),
                                rows.getString(2), rows.getString(3), rows.getString(4),
                                rows.getInt(5), rows.getTimestamp(6).toInstant());
                        leases.add(lease);
                        insertAudit(connection, tenant, lease, "CLEANUP_CLAIMED", workerId,
                                lease.attempts() + 1, null, now);
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
                statement.setObject(2, UUID.fromString(lease.productId()));
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
                statement.setObject(3, UUID.fromString(lease.productId()));
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
            insertAudit(connection, tenant, lease, "CLEANUP_DELETED", workerId,
                    lease.attempts() + 1, null, deletedAt);
            return null;
        });
    }

    @Override
    public Uni<Void> recordFailure(TenantId tenant, CleanupLease lease, String workerId,
                                   String failureCode, Instant retryAt, int maximumAttempts) {
        return transactions.withTenant(tenant, connection -> {
            String reason = failureCode == null ? "CLEANUP_FAILURE"
                    : failureCode.substring(0, Math.min(120, failureCode.length()));
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE developer_api_artifacts
                       SET lifecycle_state=CASE WHEN cleanup_attempts+1>=? THEN 'ABANDONED' ELSE 'DELETE_PENDING' END,
                           cleanup_attempts=cleanup_attempts+1,
                           next_cleanup_at=?, cleanup_lease_owner=NULL, cleanup_lease_until=NULL,
                           cleanup_last_error_code=?
                     WHERE provider_tenant_id=? AND product_id=? AND api_version=?
                       AND artifact_reference=? AND sha256=?
                       AND lifecycle_state='DELETE_PENDING' AND cleanup_lease_owner=?
                    RETURNING cleanup_attempts, lifecycle_state
                    """)) {
                statement.setInt(1, maximumAttempts);
                statement.setTimestamp(2, Timestamp.from(retryAt));
                statement.setString(3, reason);
                statement.setObject(4, tenant.value());
                statement.setObject(5, UUID.fromString(lease.productId()));
                statement.setString(6, lease.version());
                statement.setString(7, lease.artifactReference());
                statement.setString(8, lease.sha256());
                statement.setString(9, workerId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        int attempts = rows.getInt(1);
                        String state = rows.getString(2);
                        insertAudit(connection, tenant, lease,
                                "ABANDONED".equals(state) ? "TERMINAL_FAILURE" : "RETRY_SCHEDULED",
                                workerId, attempts, reason, retryAt);
                    }
                }
            }
            return null; // A stale worker cannot overwrite or audit a newer lease.
        });
    }

    @Override
    public Uni<CleanupOperationalSnapshot> snapshot(TenantId tenant, Instant now, int maximumAttempts) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (maximumAttempts < 1) return Uni.createFrom().failure(
                new IllegalArgumentException("maximumAttempts must be positive"));
        return transactions.withTenant(tenant, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT
                      count(*) FILTER (WHERE lifecycle_state IN ('ABANDONED','DELETE_PENDING')
                        AND legal_hold=FALSE AND retention_until<=?
                        AND (next_cleanup_at IS NULL OR next_cleanup_at<=?)
                        AND (cleanup_lease_until IS NULL OR cleanup_lease_until<=?)
                        AND cleanup_attempts<?) AS eligible,
                      count(*) FILTER (WHERE lifecycle_state='DELETE_PENDING'
                        AND cleanup_lease_until>?) AS leased,
                      count(*) FILTER (WHERE lifecycle_state='ABANDONED'
                        AND cleanup_attempts>=?) AS terminal_failures,
                      count(*) FILTER (WHERE lifecycle_state='DELETED') AS tombstones,
                      coalesce(max(extract(epoch FROM (? - retention_until))) FILTER (
                        WHERE lifecycle_state IN ('ABANDONED','DELETE_PENDING')
                          AND legal_hold=FALSE AND retention_until<=?
                          AND (next_cleanup_at IS NULL OR next_cleanup_at<=?)
                          AND (cleanup_lease_until IS NULL OR cleanup_lease_until<=?)
                          AND cleanup_attempts<?), 0)::bigint AS oldest_due_age_seconds
                    FROM developer_api_artifacts
                    WHERE provider_tenant_id=?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setTimestamp(3, Timestamp.from(now));
                statement.setInt(4, maximumAttempts);
                statement.setTimestamp(5, Timestamp.from(now));
                statement.setInt(6, maximumAttempts);
                statement.setTimestamp(7, Timestamp.from(now));
                statement.setTimestamp(8, Timestamp.from(now));
                statement.setTimestamp(9, Timestamp.from(now));
                statement.setTimestamp(10, Timestamp.from(now));
                statement.setInt(11, maximumAttempts);
                statement.setObject(12, tenant.value());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("Cleanup operational snapshot returned no row");
                    return new CleanupOperationalSnapshot(rows.getLong("eligible"), rows.getLong("leased"),
                            rows.getLong("terminal_failures"), rows.getLong("tombstones"),
                            rows.getLong("oldest_due_age_seconds"));
                }
            }
        });
    }

    private static void insertAudit(java.sql.Connection connection, TenantId tenant, CleanupLease lease,
                                    String action, String actor, int attempt, String reason, Instant occurredAt)
            throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO developer_api_artifact_cleanup_audit
                    (provider_tenant_id, product_id, api_version, artifact_reference, sha256,
                     action, actor_reference, attempt_number, reason_code, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, tenant.value());
            statement.setObject(2, UUID.fromString(lease.productId()));
            statement.setString(3, lease.version());
            statement.setString(4, lease.artifactReference());
            statement.setString(5, lease.sha256());
            statement.setString(6, action);
            statement.setString(7, actor);
            statement.setInt(8, Math.max(1, attempt));
            statement.setString(9, reason);
            statement.setTimestamp(10, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
    }
}
