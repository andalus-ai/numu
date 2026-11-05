package tech.kayys.numou.organization.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.*;
import tech.kayys.numou.organization.spi.OrganizationInvitationRepository;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationInvitationServiceTest {
    @Test
    void issueReturnsSecretButPersistsOnlyItsDigest() throws Exception {
        var repository = new InMemoryInvitationRepository();
        var service = new OrganizationInvitationService(repository,
                (tenant, organization, actor) -> CompletableFuture.completedFuture(null),
                Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC), new SecureRandom());

        var tenant = TenantId.generate();
        var issued = service.issue(tenant, OrganizationId.generate(), "dev@example.com",
                OrganizationMembership.Role.DEVELOPER, UserId.newId()).toCompletableFuture().join();

        assertNotNull(issued.oneTimeSecret());
        assertFalse(issued.oneTimeSecret().isBlank());
        var stored = repository.records.get(issued.invitationId());
        assertNotNull(stored);
        assertNotEquals(issued.oneTimeSecret(), stored.tokenHash());
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(issued.oneTimeSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(HexFormat.of().formatHex(digest), stored.tokenHash());
    }

    private static final class InMemoryInvitationRepository implements OrganizationInvitationRepository {
        private final Map<OrganizationInvitationId, OrganizationInvitation> records = new HashMap<>();
        public CompletionStage<OrganizationInvitation> save(OrganizationInvitation invitation, TenantId tenant) {
            if (!invitation.tenantId().equals(tenant)) return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Tenant mismatch"));
            records.put(invitation.getId(), invitation);
            return CompletableFuture.completedFuture(invitation);
        }
        public CompletionStage<Optional<OrganizationInvitation>> findByTenantAndId(
                TenantId tenant, OrganizationInvitationId id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(records.get(id))
                    .filter(value -> value.tenantId().equals(tenant)));
        }
        public CompletionStage<Optional<OrganizationInvitation>> findByTenantAndTokenHash(
                TenantId tenant, String hash) {
            return CompletableFuture.completedFuture(records.values().stream()
                    .filter(value -> value.tenantId().equals(tenant) && value.tokenHash().equals(hash)).findFirst());
        }
        public CompletionStage<Optional<OrganizationInvitation>> findPendingByTenantAndOrganizationAndEmail(
                TenantId tenant, OrganizationId organization, String email) {
            return CompletableFuture.completedFuture(records.values().stream()
                    .filter(value -> value.tenantId().equals(tenant)
                            && value.organizationId().equals(organization)
                            && value.recipientEmail().equals(email)
                            && value.status() == OrganizationInvitationStatus.PENDING).findFirst());
        }
    }
}