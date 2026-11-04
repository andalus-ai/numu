package tech.kayys.numou.security.vault.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.security.vault.domain.*;
import tech.kayys.numou.security.vault.spi.VaultPolicyRepositoryPort;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL implementation for tenant-scoped, immutable policy snapshots. */
public final class PostgresVaultPolicyRepositoryAdapter implements VaultPolicyRepositoryPort {
    private final DataSource dataSource;

    public PostgresVaultPolicyRepositoryAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public Uni<Optional<VaultPolicySnapshot>> loadActive(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        return Uni.createFrom().item(() -> loadBlocking(tenantId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Optional<VaultPolicySnapshot> loadBlocking(UUID tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                long revision;
                try (PreparedStatement head = connection.prepareStatement(
                        "SELECT active_revision FROM vault_policy_head WHERE tenant_id = ?")) {
                    head.setObject(1, tenantId);
                    try (ResultSet rs = head.executeQuery()) {
                        if (!rs.next() || rs.getLong(1) == 0) {
                            connection.commit();
                            return Optional.empty();
                        }
                        revision = rs.getLong(1);
                    }
                }
                List<VaultPolicyRule> rules = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT rule_id, subject_type, subject_value, workload_issuer, workload_audience,
                               vault_id, secret_id, operation, effect, valid_from, valid_until
                        FROM vault_policy_rule
                        WHERE tenant_id = ? AND revision = ?
                        ORDER BY rule_id
                        """)) {
                    statement.setObject(1, tenantId);
                    statement.setLong(2, revision);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) rules.add(mapRule(tenantId, revision, rs));
                    }
                }
                connection.commit();
                return Optional.of(new VaultPolicySnapshot(tenantId, revision, rules));
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new VaultPersistenceException("Unable to load active vault policy", (SQLException) failure);
            }
        } catch (SQLException failure) {
            throw new VaultPersistenceException("Unable to open vault policy read transaction", failure);
        }
    }


    @Override
    public Uni<Optional<VaultPolicySnapshot>> loadRevision(UUID tenantId, long revision) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (revision < 1) return Uni.createFrom().failure(
                new IllegalArgumentException("revision must be positive"));
        return Uni.createFrom().item(() -> loadRevisionBlocking(tenantId, revision))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Optional<VaultPolicySnapshot> loadRevisionBlocking(UUID tenantId, long revision) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement exists = connection.prepareStatement(
                        "SELECT 1 FROM vault_policy_revision WHERE tenant_id = ? AND revision = ?")) {
                    exists.setObject(1, tenantId);
                    exists.setLong(2, revision);
                    try (ResultSet rs = exists.executeQuery()) {
                        if (!rs.next()) {
                            connection.commit();
                            return Optional.empty();
                        }
                    }
                }
                List<VaultPolicyRule> rules = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT rule_id, subject_type, subject_value, workload_issuer, workload_audience,
                               vault_id, secret_id, operation, effect, valid_from, valid_until
                        FROM vault_policy_rule
                        WHERE tenant_id = ? AND revision = ?
                        ORDER BY rule_id
                        """)) {
                    statement.setObject(1, tenantId);
                    statement.setLong(2, revision);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) rules.add(mapRule(tenantId, revision, rs));
                    }
                }
                connection.commit();
                return Optional.of(new VaultPolicySnapshot(tenantId, revision, rules));
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new VaultPersistenceException("Unable to load historical vault policy", (SQLException) failure);
            }
        } catch (SQLException failure) {
            throw new VaultPersistenceException("Unable to open vault policy history transaction", failure);
        }
    }

    @Override
    public Uni<VaultPolicySnapshot> publish(UUID tenantId, long expectedRevision,
            List<VaultPolicyRule> rules, String actor, String approver, Instant publishedAt,
            VaultPolicyChangeType changeType, Long sourceRevision) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(rules, "rules are required");
        Objects.requireNonNull(publishedAt, "publishedAt is required");
        Objects.requireNonNull(changeType, "changeType is required");
        if ((changeType == VaultPolicyChangeType.ROLLBACK
                && (sourceRevision == null || sourceRevision < 1 || sourceRevision >= expectedRevision + 1))
                || (changeType == VaultPolicyChangeType.PUBLISH && sourceRevision != null))
            return Uni.createFrom().failure(new IllegalArgumentException("invalid policy change provenance"));
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) return Uni.createFrom().failure(
                new IllegalArgumentException("expectedRevision must be within [0, Long.MAX_VALUE)"));
        if (actor == null || actor.isBlank() || actor.length() > 200
                || approver == null || approver.isBlank() || approver.length() > 200
                || actor.equals(approver))
            return Uni.createFrom().failure(new IllegalArgumentException(
                    "actor and independent approver must be non-empty, bounded and distinct"));
        List<VaultPolicyRule> immutableRules = List.copyOf(rules);
        long nextRevision = expectedRevision + 1;
        try {
            new VaultPolicySnapshot(tenantId, nextRevision, immutableRules);
        } catch (RuntimeException invalid) {
            return Uni.createFrom().failure(invalid);
        }
        return Uni.createFrom().item(() -> publishBlocking(tenantId, expectedRevision,
                nextRevision, immutableRules, actor, approver, publishedAt, changeType, sourceRevision))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private VaultPolicySnapshot publishBlocking(UUID tenantId, long expected, long revision,
            List<VaultPolicyRule> rules, String actor, String approver, Instant publishedAt,
            VaultPolicyChangeType changeType, Long sourceRevision) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement initialize = connection.prepareStatement("""
                        INSERT INTO vault_policy_head (tenant_id, active_revision, updated_at)
                        VALUES (?, 0, ?) ON CONFLICT (tenant_id) DO NOTHING
                        """)) {
                    initialize.setObject(1, tenantId);
                    initialize.setTimestamp(2, Timestamp.from(publishedAt));
                    initialize.executeUpdate();
                }
                long actual;
                try (PreparedStatement lock = connection.prepareStatement("""
                        SELECT active_revision FROM vault_policy_head WHERE tenant_id = ? FOR UPDATE
                        """)) {
                    lock.setObject(1, tenantId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("policy head disappeared while locked");
                        actual = rs.getLong(1);
                    }
                }
                if (actual != expected) throw new VaultPolicyRevisionConflictException(expected, actual);

                try (PreparedStatement revisionInsert = connection.prepareStatement("""
                        INSERT INTO vault_policy_revision
                            (tenant_id, revision, published_by, approved_by, published_at, rule_count, change_type, source_revision)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    revisionInsert.setObject(1, tenantId);
                    revisionInsert.setLong(2, revision);
                    revisionInsert.setString(3, actor);
                    revisionInsert.setString(4, approver);
                    revisionInsert.setTimestamp(5, Timestamp.from(publishedAt));
                    revisionInsert.setInt(6, rules.size());
                    revisionInsert.setString(7, changeType.name());
                    if (sourceRevision == null) revisionInsert.setNull(8, Types.BIGINT);
                    else revisionInsert.setLong(8, sourceRevision);
                    revisionInsert.executeUpdate();
                }

                try (PreparedStatement ruleInsert = connection.prepareStatement("""
                        INSERT INTO vault_policy_rule
                            (tenant_id, revision, rule_id, subject_type, subject_value,
                             workload_issuer, workload_audience, vault_id, secret_id,
                             operation, effect, valid_from, valid_until)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (VaultPolicyRule rule : rules) {
                        ruleInsert.setObject(1, tenantId);
                        ruleInsert.setLong(2, revision);
                        ruleInsert.setObject(3, rule.ruleId());
                        ruleInsert.setString(4, rule.subjectType().name());
                        ruleInsert.setString(5, rule.subject());
                        setNullableString(ruleInsert, 6, rule.workloadIssuer());
                        setNullableString(ruleInsert, 7, rule.workloadAudience());
                        setNullableUuid(ruleInsert, 8, rule.vaultId() == null ? null : rule.vaultId().value());
                        setNullableUuid(ruleInsert, 9, rule.secretId() == null ? null : rule.secretId().value());
                        ruleInsert.setString(10, rule.operation().name());
                        ruleInsert.setString(11, rule.effect().name());
                        ruleInsert.setTimestamp(12, Timestamp.from(rule.validFrom()));
                        if (rule.validUntil() == null) ruleInsert.setNull(13, Types.TIMESTAMP_WITH_TIMEZONE);
                        else ruleInsert.setTimestamp(13, Timestamp.from(rule.validUntil()));
                        ruleInsert.addBatch();
                    }
                    ruleInsert.executeBatch();
                }

                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE vault_policy_head SET active_revision = ?, updated_at = ?
                        WHERE tenant_id = ? AND active_revision = ?
                        """)) {
                    update.setLong(1, revision);
                    update.setTimestamp(2, Timestamp.from(publishedAt));
                    update.setObject(3, tenantId);
                    update.setLong(4, expected);
                    if (update.executeUpdate() != 1)
                        throw new VaultPolicyRevisionConflictException(expected, actual);
                }

                try (PreparedStatement audit = connection.prepareStatement("""
                        INSERT INTO vault_policy_admin_audit
                            (event_id, tenant_id, revision, actor, approved_by, event_type, occurred_at, rule_count)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    audit.setObject(1, UUID.randomUUID());
                    audit.setObject(2, tenantId);
                    audit.setLong(3, revision);
                    audit.setString(4, actor);
                    audit.setString(5, approver);
                    audit.setString(6, changeType == VaultPolicyChangeType.ROLLBACK
                            ? "POLICY_ROLLED_BACK" : "POLICY_PUBLISHED");
                    audit.setTimestamp(7, Timestamp.from(publishedAt));
                    audit.setInt(8, rules.size());
                    audit.executeUpdate();
                }
                connection.commit();
                return new VaultPolicySnapshot(tenantId, revision, rules);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new VaultPersistenceException("Unable to publish vault policy revision", (SQLException) failure);
            }
        } catch (SQLException failure) {
            throw new VaultPersistenceException("Unable to open vault policy publication transaction", failure);
        }
    }

    private static VaultPolicyRule mapRule(UUID tenantId, long revision, ResultSet rs) throws SQLException {
        UUID vault = rs.getObject("vault_id", UUID.class);
        UUID secret = rs.getObject("secret_id", UUID.class);
        Timestamp until = rs.getTimestamp("valid_until");
        return new VaultPolicyRule(rs.getObject("rule_id", UUID.class), tenantId, revision,
                VaultPolicySubjectType.valueOf(rs.getString("subject_type")),
                rs.getString("subject_value"), rs.getString("workload_issuer"),
                rs.getString("workload_audience"), vault == null ? null : VaultId.of(vault),
                secret == null ? null : SecretId.of(secret),
                VaultOperation.valueOf(rs.getString("operation")),
                VaultPolicyEffect.valueOf(rs.getString("effect")),
                rs.getTimestamp("valid_from").toInstant(), until == null ? null : until.toInstant());
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static void setNullableUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) statement.setNull(index, Types.OTHER);
        else statement.setObject(index, value);
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }
}
