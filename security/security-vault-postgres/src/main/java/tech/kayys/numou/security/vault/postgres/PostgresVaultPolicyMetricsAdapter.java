package tech.kayys.numou.security.vault.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.security.vault.spi.VaultPolicyMetricsPort;
import tech.kayys.numou.security.vault.spi.VaultPolicyOperationalMetrics;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped policy metrics from the authoritative PostgreSQL policy and audit tables. */
public final class PostgresVaultPolicyMetricsAdapter implements VaultPolicyMetricsPort {
    private final DataSource dataSource;

    public PostgresVaultPolicyMetricsAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public Uni<VaultPolicyOperationalMetrics> load(UUID tenantId, Instant windowStart, Instant measuredAt) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(windowStart, "windowStart is required");
        Objects.requireNonNull(measuredAt, "measuredAt is required");
        if (measuredAt.isBefore(windowStart))
            return Uni.createFrom().failure(new IllegalArgumentException("measuredAt must not precede windowStart"));
        return Uni.createFrom().item(() -> loadBlocking(tenantId, windowStart, measuredAt))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private VaultPolicyOperationalMetrics loadBlocking(UUID tenantId, Instant windowStart, Instant measuredAt) {
        String sql = """
                SELECT COALESCE(h.active_revision, 0) AS active_revision,
                       COALESCE((SELECT COUNT(*) FROM vault_policy_rule r
                                 WHERE r.tenant_id = ? AND r.revision = h.active_revision), 0) AS rule_count,
                       (SELECT COUNT(*) FROM vault_policy_decision_audit d
                        WHERE d.tenant_id = ? AND d.effect = 'ALLOW'
                          AND d.evaluated_at >= ? AND d.evaluated_at <= ?) AS allowed_count,
                       (SELECT COUNT(*) FROM vault_policy_decision_audit d
                        WHERE d.tenant_id = ? AND d.effect = 'DENY'
                          AND d.evaluated_at >= ? AND d.evaluated_at <= ?) AS denied_count
                FROM (SELECT 1) seed
                LEFT JOIN vault_policy_head h ON h.tenant_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, tenantId);
            statement.setTimestamp(3, Timestamp.from(windowStart));
            statement.setTimestamp(4, Timestamp.from(measuredAt));
            statement.setObject(5, tenantId);
            statement.setTimestamp(6, Timestamp.from(windowStart));
            statement.setTimestamp(7, Timestamp.from(measuredAt));
            statement.setObject(8, tenantId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new SQLException("policy metrics query returned no row");
                return new VaultPolicyOperationalMetrics(tenantId, rs.getLong("active_revision"),
                        rs.getLong("rule_count"), rs.getLong("allowed_count"), rs.getLong("denied_count"),
                        windowStart, measuredAt);
            }
        } catch (SQLException failure) {
            throw new VaultPersistenceException("Unable to load vault policy operational metrics", failure);
        }
    }
}
