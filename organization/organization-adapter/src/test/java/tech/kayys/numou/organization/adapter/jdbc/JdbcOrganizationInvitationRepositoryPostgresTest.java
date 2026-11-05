package tech.kayys.numou.organization.adapter.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.*;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcOrganizationInvitationRepositoryPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void invitationReadsAreTenantScopedAndOnlyDigestIsStored() throws Exception {
        var repository = repository();
        var tenantA = TenantId.generate();
        var tenantB = TenantId.generate();
        var now = Instant.now();
        var invitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(),
                tenantA, OrganizationId.generate(), "dev@example.com",
                OrganizationMembership.Role.DEVELOPER, UserId.newId(), "a".repeat(64),
                now.plus(7, ChronoUnit.DAYS), now);
        repository.save(invitation, tenantA).toCompletableFuture().join();

        assertTrue(repository.findByTenantAndId(tenantA, invitation.getId()).toCompletableFuture().join().isPresent());
        assertTrue(repository.findByTenantAndId(tenantB, invitation.getId()).toCompletableFuture().join().isEmpty());
        assertTrue(repository.findByTenantAndTokenHash(tenantA, "a".repeat(64)).toCompletableFuture().join().isPresent());
        assertTrue(repository.findByTenantAndTokenHash(tenantB, "a".repeat(64)).toCompletableFuture().join().isEmpty());
        assertTrue(repository.findPendingByTenantAndOrganizationAndEmail(
                tenantA, invitation.organizationId(), "dev@example.com").toCompletableFuture().join().isPresent());
    }

    @Test
    void acceptanceConsumesSecretAndCreatesMembershipInOneTransaction() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var user = UserId.newId();
        var now = Instant.now();
        var invitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(), tenant,
                OrganizationId.generate(), "dev@example.com", OrganizationMembership.Role.DEVELOPER,
                UserId.newId(), "d".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
        repository.save(invitation, tenant).toCompletableFuture().join();

        var accepted = repository.accept(tenant, "d".repeat(64), user,
                "DEV@example.com", now.plusSeconds(1)).toCompletableFuture().join();

        assertEquals(invitation.getId(), accepted.invitationId());
        assertEquals(user, accepted.userId());
        assertTrue(repository.findByTenantAndId(tenant, invitation.getId()).toCompletableFuture()
                .join().orElseThrow().status() == OrganizationInvitationStatus.ACCEPTED);
        try (Connection c = dataSource().getConnection();
             var s = c.prepareStatement("SELECT status, role FROM organization_memberships WHERE tenant_id = ? AND user_id = ?")) {
            s.setObject(1, tenant.value());
            s.setObject(2, user.value());
            try (var rs = s.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ACTIVE", rs.getString("status"));
                assertEquals("DEVELOPER", rs.getString("role"));
            }
        }
        assertThrows(RuntimeException.class, () -> repository.accept(tenant, "d".repeat(64), user,
                "dev@example.com", now.plusSeconds(2)).toCompletableFuture().join());
    }

    @Test
    void mismatchedVerifiedEmailLeavesInvitationPending() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var now = Instant.now();
        var invitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(), tenant,
                OrganizationId.generate(), "dev@example.com", OrganizationMembership.Role.VIEWER,
                UserId.newId(), "e".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
        repository.save(invitation, tenant).toCompletableFuture().join();

        assertThrows(RuntimeException.class, () -> repository.accept(tenant, "e".repeat(64), UserId.newId(),
                "other@example.com", now.plusSeconds(1)).toCompletableFuture().join());
        assertEquals(OrganizationInvitationStatus.PENDING, repository.findByTenantAndId(tenant, invitation.getId())
                .toCompletableFuture().join().orElseThrow().status());
    }

    @Test
    void resendUsesOptimisticVersionAndRotatesStoredDigest() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var now = Instant.now();
        var invitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(), tenant,
                OrganizationId.generate(), "dev@example.com", OrganizationMembership.Role.VIEWER,
                UserId.newId(), "a".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
        repository.save(invitation, tenant).toCompletableFuture().join();

        var first = repository.findByTenantAndId(tenant, invitation.getId()).toCompletableFuture().join().orElseThrow();
        var stale = repository.findByTenantAndId(tenant, invitation.getId()).toCompletableFuture().join().orElseThrow();
        first.rotate("b".repeat(64), now.plus(14, ChronoUnit.DAYS), now.plusSeconds(1));
        repository.save(first, tenant).toCompletableFuture().join();
        stale.rotate("c".repeat(64), now.plus(14, ChronoUnit.DAYS), now.plusSeconds(2));
        assertThrows(RuntimeException.class, () -> repository.save(stale, tenant).toCompletableFuture().join());

        var stored = repository.findByTenantAndId(tenant, invitation.getId()).toCompletableFuture().join().orElseThrow();
        assertEquals("b".repeat(64), stored.tokenHash());
        assertEquals(1, stored.getVersion());
    }

    private JdbcOrganizationInvitationRepository repository() throws Exception {
        DataSource source = dataSource();
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS organization_invitations CASCADE");
            statement.execute("DROP TABLE IF EXISTS organization_memberships CASCADE");
            try (InputStream membershipMigration = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V3__organization_memberships.sql")) {
                assertNotNull(membershipMigration);
                statement.execute(new String(membershipMigration.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream input = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V4__organization_invitations.sql")) {
                assertNotNull(input);
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return new JdbcOrganizationInvitationRepository(source, Runnable::run);
    }

    private DataSource dataSource() {
        var source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }
}