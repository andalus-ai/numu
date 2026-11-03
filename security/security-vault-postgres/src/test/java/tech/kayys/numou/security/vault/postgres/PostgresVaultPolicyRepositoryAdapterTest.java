package tech.kayys.numou.security.vault.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.security.vault.domain.*;
import tech.kayys.numou.security.vault.spi.VaultPolicyDecisionAudit;
import tech.kayys.numou.security.vault.spi.VaultPolicyDecisionAuditPort;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresVaultPolicyRepositoryAdapterTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private PostgresVaultPolicyRepositoryAdapter policies;
    private VaultPolicyDecisionAuditPort decisions;
    private PostgresVaultPolicyMetricsAdapter metrics;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        policies = new PostgresVaultPolicyRepositoryAdapter(dataSource);
        decisions = new PostgresVaultPolicyDecisionAuditAdapter(dataSource);
        metrics = new PostgresVaultPolicyMetricsAdapter(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS vault_policy_decision_audit CASCADE");
            statement.execute("DROP TABLE IF EXISTS vault_policy_admin_audit CASCADE");
            statement.execute("DROP TABLE IF EXISTS vault_policy_rule CASCADE");
            statement.execute("DROP TABLE IF EXISTS vault_policy_revision CASCADE");
            statement.execute("DROP TABLE IF EXISTS vault_policy_head CASCADE");
            statement.execute("DROP FUNCTION IF EXISTS reject_vault_policy_revision_mutation() CASCADE");
            try (InputStream migration = getClass().getResourceAsStream(
                    "/db/migration/V3__vault_policy_revisions_and_decision_audit.sql")) {
                assertNotNull(migration);
                for (String command : splitSql(new String(migration.readAllBytes(), StandardCharsets.UTF_8))) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }

    @Test
    void publishesImmutableTenantScopedRevisionAndRejectsStaleWriter() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID principal = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-03T10:00:00Z");
        VaultPolicyRule rule = rule(tenant, 1, principal, now);

        VaultPolicySnapshot published = policies.publish(tenant, 0, List.of(rule), "security-admin", "independent-approver", now, VaultPolicyChangeType.PUBLISH, null)
                .await().indefinitely();

        assertEquals(1, published.revision());
        assertEquals(1, policies.loadActive(tenant).await().indefinitely().orElseThrow().rules().size());
        assertTrue(policies.loadActive(UUID.randomUUID()).await().indefinitely().isEmpty());
        assertThrows(RuntimeException.class, () -> policies.publish(tenant, 0, List.of(rule), "stale-admin", "independent-approver", now, VaultPolicyChangeType.PUBLISH, null)
                .await().indefinitely());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(
                    "UPDATE vault_policy_rule SET effect = 'DENY' WHERE tenant_id = '" + tenant
                            + "' AND revision = 1"));
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO vault_policy_rule (tenant_id, revision, rule_id, subject_type, subject_value, "
                            + "operation, effect, valid_from) VALUES ('" + tenant
                            + "', 1, '" + UUID.randomUUID() + "', 'PRINCIPAL_ID', 'operator', "
                            + "'READ', 'ALLOW', CURRENT_TIMESTAMP)"));
            try (ResultSet rs = statement.executeQuery(
                    "SELECT count(*) FROM vault_policy_admin_audit WHERE tenant_id = '" + tenant + "'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void rejectsSelfApprovalBeforeOpeningPersistenceTransaction() {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        assertThrows(RuntimeException.class, () -> policies.publish(tenant, 0,
                List.of(rule(tenant, 1, UUID.randomUUID(), now)), "same-admin", "same-admin",
                now, VaultPolicyChangeType.PUBLISH, null).await().indefinitely());
        assertTrue(policies.loadActive(tenant).await().indefinitely().isEmpty());
    }

    @Test
    void auditFailureRollsBackPublicationAndActiveRevision() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE OR REPLACE FUNCTION reject_policy_admin_audit() RETURNS trigger
                    LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'audit unavailable'; END; $$
                    """);
            statement.execute("""
                    CREATE TRIGGER reject_policy_admin_audit BEFORE INSERT ON vault_policy_admin_audit
                    FOR EACH ROW EXECUTE FUNCTION reject_policy_admin_audit()
                    """);
        }
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        assertThrows(RuntimeException.class, () -> policies.publish(tenant, 0,
                List.of(rule(tenant, 1, UUID.randomUUID(), now)), "security-admin", "independent-approver", now, VaultPolicyChangeType.PUBLISH, null).await().indefinitely());
        assertTrue(policies.loadActive(tenant).await().indefinitely().isEmpty());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM vault_policy_revision")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void decisionAuditPersistsAllowAndDenyMetadata() throws Exception {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        var allow = new VaultPolicyDecision(tenant, VaultOperation.READ, VaultPolicyEffect.ALLOW,
                1, Optional.of(UUID.randomUUID()), "MATCHED_ALLOW", now);
        var deny = new VaultPolicyDecision(tenant, VaultOperation.READ, VaultPolicyEffect.DENY,
                0, Optional.empty(), "NO_MATCH_DEFAULT_DENY", now);
        decisions.append(new VaultPolicyDecisionAudit(UUID.randomUUID(), "principal:operator",
                vault, secret, allow, "corr-allow")).await().indefinitely();
        decisions.append(new VaultPolicyDecisionAudit(UUID.randomUUID(), "workload:payments",
                vault, secret, deny, null)).await().indefinitely();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT effect, policy_revision FROM vault_policy_decision_audit ORDER BY effect")) {
            assertTrue(rs.next());
            assertEquals("ALLOW", rs.getString(1));
            assertEquals(1, rs.getLong(2));
            assertTrue(rs.next());
            assertEquals("DENY", rs.getString(1));
            assertEquals(0, rs.getLong(2));
        }
    }


    @Test
    void rollbackPublishesFreshRevisionAndRetainsHistoricalSnapshot() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID principal = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-03T10:00:00Z");
        policies.publish(tenant, 0, List.of(rule(tenant, 1, principal, now)),
                "security-admin", "independent-approver", now, VaultPolicyChangeType.PUBLISH, null).await().indefinitely();

        VaultPolicyRule changed = new VaultPolicyRule(UUID.randomUUID(), tenant, 2,
                VaultPolicySubjectType.PRINCIPAL_ID, principal.toString(), null, null,
                null, null, VaultOperation.READ, VaultPolicyEffect.DENY, now.minusSeconds(1), null);
        policies.publish(tenant, 1, List.of(changed), "security-admin", "independent-approver", now, VaultPolicyChangeType.PUBLISH, null).await().indefinitely();

        var historical = policies.loadRevision(tenant, 1).await().indefinitely().orElseThrow();
        var restoredRules = historical.rules().stream().map(rule -> new VaultPolicyRule(
                rule.ruleId(), tenant, 3, rule.subjectType(), rule.subject(), rule.workloadIssuer(),
                rule.workloadAudience(), rule.vaultId(), rule.secretId(), rule.operation(),
                rule.effect(), rule.validFrom(), rule.validUntil())).toList();
        var rollback = policies.publish(tenant, 2, restoredRules, "security-admin", "independent-approver", now,
                VaultPolicyChangeType.ROLLBACK, 1L).await().indefinitely();

        assertEquals(3, rollback.revision());
        assertEquals(VaultPolicyEffect.ALLOW, rollback.rules().get(0).effect());
        assertEquals(VaultPolicyEffect.DENY, policies.loadRevision(tenant, 2)
                .await().indefinitely().orElseThrow().rules().get(0).effect());
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT change_type, source_revision, approved_by FROM vault_policy_revision WHERE tenant_id = ? AND revision = 3")) {
            statement.setObject(1, tenant);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ROLLBACK", rs.getString(1));
                assertEquals(1L, rs.getLong(2));
                assertEquals("independent-approver", rs.getString(3));
            }
        }
    }

    @Test
    void operationalMetricsAreTenantScopedAndWindowBounded() {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-03T10:00:00Z");
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        var allowed = new VaultPolicyDecision(tenant, VaultOperation.READ, VaultPolicyEffect.ALLOW,
                0, Optional.empty(), "NO_MATCH_DEFAULT_DENY", now.minusSeconds(10));
        var denied = new VaultPolicyDecision(tenant, VaultOperation.READ, VaultPolicyEffect.DENY,
                0, Optional.empty(), "NO_MATCH_DEFAULT_DENY", now.minusSeconds(5));
        decisions.append(new VaultPolicyDecisionAudit(UUID.randomUUID(), "principal:a",
                vault, secret, allowed, null)).await().indefinitely();
        decisions.append(new VaultPolicyDecisionAudit(UUID.randomUUID(), "principal:b",
                vault, secret, denied, null)).await().indefinitely();

        var result = metrics.load(tenant, now.minusSeconds(30), now).await().indefinitely();
        var other = metrics.load(UUID.randomUUID(), now.minusSeconds(30), now).await().indefinitely();
        assertEquals(1, result.decisionsAllowed());
        assertEquals(1, result.decisionsDenied());
        assertEquals(0, other.decisionsAllowed());
        assertEquals(0, other.decisionsDenied());
    }

    private static VaultPolicyRule rule(UUID tenant, long revision, UUID principal, Instant now) {
        return new VaultPolicyRule(UUID.randomUUID(), tenant, revision,
                VaultPolicySubjectType.PRINCIPAL_ID, principal.toString(), null, null,
                null, null, VaultOperation.READ, VaultPolicyEffect.ALLOW, now.minusSeconds(1), null);
    }

    /** Split SQL on semicolons outside PostgreSQL dollar-quoted function bodies. */
    private static List<String> splitSql(String sql) {
        java.util.ArrayList<String> commands = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean dollarQuoted = false;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.startsWith("$$", i)) {
                dollarQuoted = !dollarQuoted;
                current.append("$$");
                i++;
            } else if (sql.charAt(i) == ';' && !dollarQuoted) {
                if (!current.toString().isBlank()) commands.add(current.toString());
                current.setLength(0);
            } else {
                current.append(sql.charAt(i));
            }
        }
        if (!current.toString().isBlank()) commands.add(current.toString());
        return commands;
    }
}
