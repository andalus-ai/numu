package tech.kayys.numou.organization.adapter.jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.*;
import tech.kayys.numou.organization.application.InvitationSecretCipherPort;
import tech.kayys.numou.organization.application.OrganizationInvitationDeliveryOutboxPort;

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

    @Test
    void issuancePersistsInvitationAndEncryptedDeliveryAtomically() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var now = Instant.now();
        var invitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(), tenant,
                OrganizationId.generate(), "queued@example.com", OrganizationMembership.Role.DEVELOPER,
                UserId.newId(), "f".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
        var deliveryId = java.util.UUID.randomUUID();
        var encrypted = new InvitationSecretCipherPort.EncryptedSecret("TEST", 1, "key-1",
                java.util.UUID.randomUUID().toString(), invitation.getId().value().toString(), new byte[]{1, 2, 3});
        var delivery = new OrganizationInvitationDeliveryOutboxPort.DeliveryMessage(
                deliveryId, tenant, invitation.getId(), invitation.recipientEmail(), encrypted, now);

        repository.persist(invitation, delivery, tenant).toCompletableFuture().join();

        assertTrue(repository.findByTenantAndId(tenant, invitation.getId()).toCompletableFuture().join().isPresent());
        try (Connection c = dataSource().getConnection();
             var s = c.prepareStatement("SELECT secret_ciphertext FROM organization_invitation_delivery_outbox "
                     + "WHERE tenant_id=? AND invitation_id=?")) {
            s.setObject(1, tenant.value());
            s.setObject(2, invitation.getId().value());
            try (var rs = s.executeQuery()) {
                assertTrue(rs.next());
                assertArrayEquals(new byte[]{1, 2, 3}, rs.getBytes(1));
            }
        }

        var duplicateInvitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(), tenant,
                OrganizationId.generate(), "rollback@example.com", OrganizationMembership.Role.VIEWER,
                UserId.newId(), "g".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
        var duplicateDelivery = new OrganizationInvitationDeliveryOutboxPort.DeliveryMessage(
                deliveryId, tenant, duplicateInvitation.getId(), duplicateInvitation.recipientEmail(),
                encrypted, now);
        assertThrows(RuntimeException.class, () -> repository.persist(
                duplicateInvitation, duplicateDelivery, tenant).toCompletableFuture().join());
        assertTrue(repository.findByTenantAndId(tenant, duplicateInvitation.getId())
                .toCompletableFuture().join().isEmpty(), "invitation insert must roll back with outbox failure");
    }

    @Test
    void outboxClaimsAreTenantScopedAndStaleLeaseTokensCannotComplete() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var now = Instant.now();
        var invitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(), tenant,
                OrganizationId.generate(), "worker@example.com", OrganizationMembership.Role.DEVELOPER,
                UserId.newId(), "h".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
        var encrypted = new InvitationSecretCipherPort.EncryptedSecret("TEST", 1, "key-1",
                java.util.UUID.randomUUID().toString(), invitation.getId().value().toString(), new byte[]{4, 5});
        var delivery = new OrganizationInvitationDeliveryOutboxPort.DeliveryMessage(
                java.util.UUID.randomUUID(), tenant, invitation.getId(), invitation.recipientEmail(), encrypted, now);
        repository.persist(invitation, delivery, tenant).toCompletableFuture().join();

        var outbox = new JdbcOrganizationInvitationDeliveryOutbox(dataSource(), Runnable::run);
        var claimed = outbox.claim(now, now.plusSeconds(30), 10).toCompletableFuture().join();
        assertEquals(1, claimed.size());
        var item = claimed.get(0);
        assertEquals(tenant, item.tenantId());
        assertEquals(1, item.attempts());
        assertFalse(outbox.markSent(item.deliveryId(), TenantId.generate(), item.leaseToken(), now)
                .toCompletableFuture().join());
        assertFalse(outbox.markSent(item.deliveryId(), tenant, java.util.UUID.randomUUID(), now)
                .toCompletableFuture().join());
        assertTrue(outbox.retry(item.deliveryId(), tenant, item.leaseToken(), now.plusSeconds(5), "EMAIL_PROVIDER_FAILED")
                .toCompletableFuture().join());
        assertTrue(outbox.claim(now.plusSeconds(6), now.plusSeconds(36), 10).toCompletableFuture().join().size() == 1);
    }

    @Test
    void secureResendRotatesDigestAndSupersedesOldLeasedDeliveryAtomically() throws Exception {
        var repository = repository();
        var tenant = TenantId.generate();
        var now = Instant.now();
        var invitation = OrganizationInvitation.issue(OrganizationInvitationId.generate(), tenant,
                OrganizationId.generate(), "resend@example.com", OrganizationMembership.Role.DEVELOPER,
                UserId.newId(), "i".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
        var vaultId = java.util.UUID.randomUUID().toString();
        var firstSecret = new InvitationSecretCipherPort.EncryptedSecret("TEST", 1, "key-1",
                vaultId, invitation.getId().value().toString(), new byte[]{1});
        var firstDelivery = new OrganizationInvitationDeliveryOutboxPort.DeliveryMessage(
                java.util.UUID.randomUUID(), tenant, invitation.getId(), invitation.recipientEmail(), firstSecret, now);
        repository.persist(invitation, firstDelivery, tenant).toCompletableFuture().join();

        var outbox = new JdbcOrganizationInvitationDeliveryOutbox(dataSource(), Runnable::run);
        var leased = outbox.claim(now, now.plusSeconds(60), 10).toCompletableFuture().join().get(0);
        var rotated = repository.findByTenantAndId(tenant, invitation.getId())
                .toCompletableFuture().join().orElseThrow();
        int expectedVersion = rotated.getVersion();
        Instant resentAt = now.plusSeconds(1);
        rotated.rotate("j".repeat(64), resentAt.plus(7, ChronoUnit.DAYS), resentAt);
        var secondSecret = new InvitationSecretCipherPort.EncryptedSecret("TEST", 1, "key-1",
                vaultId, invitation.getId().value().toString(), new byte[]{2});
        var secondDelivery = new OrganizationInvitationDeliveryOutboxPort.DeliveryMessage(
                java.util.UUID.randomUUID(), tenant, invitation.getId(), invitation.recipientEmail(),
                secondSecret, resentAt);

        repository.rotateAndPersist(rotated, secondDelivery, tenant, expectedVersion, resentAt)
                .toCompletableFuture().join();

        assertEquals("j".repeat(64), repository.findByTenantAndId(tenant, invitation.getId())
                .toCompletableFuture().join().orElseThrow().tokenHash());
        assertFalse(outbox.markSent(leased.deliveryId(), tenant, leased.leaseToken(), resentAt)
                .toCompletableFuture().join(), "superseded worker must lose its lease fence");
        try (Connection c = dataSource().getConnection();
             var s = c.prepareStatement("SELECT delivery_id, status, last_error_code FROM "
                     + "organization_invitation_delivery_outbox WHERE tenant_id=? AND invitation_id=? "
                     + "ORDER BY created_at, delivery_id")) {
            s.setObject(1, tenant.value());
            s.setObject(2, invitation.getId().value());
            try (var rs = s.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(leased.deliveryId(), rs.getObject("delivery_id", java.util.UUID.class));
                assertEquals("DEAD", rs.getString("status"));
                assertEquals("SUPERSEDED_BY_RESEND", rs.getString("last_error_code"));
                assertTrue(rs.next());
                assertEquals(secondDelivery.deliveryId(), rs.getObject("delivery_id", java.util.UUID.class));
                assertEquals("PENDING", rs.getString("status"));
            }
        }
    }

    private JdbcOrganizationInvitationRepository repository() throws Exception {
        DataSource source = dataSource();
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS organization_invitation_delivery_outbox CASCADE");
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
            try (InputStream outboxMigration = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V5__organization_invitation_delivery_outbox.sql")) {
                assertNotNull(outboxMigration);
                statement.execute(new String(outboxMigration.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream resendMigration = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V6__organization_invitation_delivery_resend.sql")) {
                assertNotNull(resendMigration);
                statement.execute(new String(resendMigration.readAllBytes(), StandardCharsets.UTF_8));
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