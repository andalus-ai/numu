package tech.kayys.numou.organization.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.*;
import tech.kayys.numou.organization.spi.OrganizationInvitationRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationInvitationDeliveryWorkerTest {
    private static final Instant NOW = Instant.parse("2026-11-05T05:00:00Z");

    @Test
    void staleSecretIsDeadLetteredWithoutCallingEmailProvider() throws Exception {
        String currentSecret = "current-secret";
        var fixture = fixture(currentSecret, "old-secret", NOW.plusSeconds(3600));
        var sends = new ArrayList<InvitationEmailProvider.Email>();
        var worker = worker(fixture, sends);

        worker.runBatch(10).toCompletableFuture().join();

        assertTrue(sends.isEmpty());
        assertEquals("INVITATION_NOT_CURRENT", fixture.outbox.deadLetterCode);
        assertEquals(0, fixture.outbox.sentCount);
    }

    @Test
    void expiredInvitationIsDeadLetteredWithoutCallingEmailProvider() throws Exception {
        var fixture = fixture("expired-secret", "expired-secret", NOW.minusSeconds(1));
        var sends = new ArrayList<InvitationEmailProvider.Email>();
        worker(fixture, sends).runBatch(10).toCompletableFuture().join();

        assertTrue(sends.isEmpty());
        assertEquals("INVITATION_NOT_CURRENT", fixture.outbox.deadLetterCode);
    }

    @Test
    void currentInvitationIsSentWithDeliveryIdempotencyKey() throws Exception {
        String secret = "current-secret";
        var fixture = fixture(secret, secret, NOW.plusSeconds(3600));
        var sends = new ArrayList<InvitationEmailProvider.Email>();
        worker(fixture, sends).runBatch(10).toCompletableFuture().join();

        assertEquals(1, sends.size());
        assertEquals(fixture.deliveryId, sends.get(0).idempotencyKey());
        assertTrue(sends.get(0).invitationUrl().contains(secret));
        assertEquals(1, fixture.outbox.sentCount);
        assertNull(fixture.outbox.deadLetterCode);
    }

    @Test
    void providerFailureSchedulesRetryInsteadOfMarkingSent() throws Exception {
        var fixture = fixture("current-secret", "current-secret", NOW.plusSeconds(3600));
        var worker = new OrganizationInvitationDeliveryWorker(fixture.outbox, fixture.repository,
                fixture.cipher, email -> CompletableFuture.failedFuture(new IllegalStateException("provider down")),
                Clock.fixed(NOW, ZoneOffset.UTC), URI.create("https://portal.example.test"),
                java.time.Duration.ofMinutes(2));

        worker.runBatch(10).toCompletableFuture().join();

        assertEquals(0, fixture.outbox.sentCount);
        assertEquals("EMAIL_PROVIDER_FAILED", fixture.outbox.retryCode);
        assertNotNull(fixture.outbox.retryAt);
    }

    private static OrganizationInvitationDeliveryWorker worker(Fixture f,
            List<InvitationEmailProvider.Email> sends) {
        return new OrganizationInvitationDeliveryWorker(f.outbox, f.repository, f.cipher,
                email -> { sends.add(email); return CompletableFuture.completedFuture(null); },
                Clock.fixed(NOW, ZoneOffset.UTC), URI.create("https://portal.example.test"), java.time.Duration.ofMinutes(2));
    }

    private static Fixture fixture(String currentSecret, String queuedSecret, Instant expiresAt) throws Exception {
        var tenant = TenantId.generate();
        var invitationId = OrganizationInvitationId.generate();
        var invitation = OrganizationInvitation.issue(invitationId, tenant, OrganizationId.generate(),
                "recipient@example.test", OrganizationMembership.Role.DEVELOPER, UserId.newId(),
                digest(currentSecret), expiresAt, NOW.minusSeconds(60));
        var repository = new SingleInvitationRepository(invitation);
        var deliveryId = UUID.randomUUID();
        var encrypted = new InvitationSecretCipherPort.EncryptedSecret("TEST", 1, "key",
                UUID.randomUUID().toString(), invitationId.value().toString(), new byte[]{1});
        var claimed = new OrganizationInvitationDeliveryOutboxPort.ClaimedDelivery(deliveryId, tenant,
                invitationId, invitation.recipientEmail(), encrypted, 1, UUID.randomUUID(), NOW.plusSeconds(120));
        var outbox = new SingleClaimOutbox(claimed);
        InvitationSecretCipherPort cipher = new InvitationSecretCipherPort() {
            public EncryptedSecret encrypt(TenantId t, OrganizationInvitationId id, String plaintext) {
                throw new UnsupportedOperationException();
            }
            public String decrypt(TenantId t, OrganizationInvitationId id, EncryptedSecret value) {
                return queuedSecret;
            }
        };
        return new Fixture(repository, outbox, cipher, deliveryId);
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Fixture(SingleInvitationRepository repository, SingleClaimOutbox outbox,
                           InvitationSecretCipherPort cipher, UUID deliveryId) { }

    private static final class SingleInvitationRepository implements OrganizationInvitationRepository {
        private final OrganizationInvitation invitation;
        private SingleInvitationRepository(OrganizationInvitation invitation) { this.invitation = invitation; }
        public CompletionStage<OrganizationInvitation> save(OrganizationInvitation value, TenantId tenant) {
            return CompletableFuture.completedFuture(value);
        }
        public CompletionStage<Optional<OrganizationInvitation>> findByTenantAndId(TenantId tenant,
                OrganizationInvitationId id) {
            return CompletableFuture.completedFuture(invitation.tenantId().equals(tenant)
                    && invitation.getId().equals(id) ? Optional.of(invitation) : Optional.empty());
        }
        public CompletionStage<Optional<OrganizationInvitation>> findByTenantAndTokenHash(TenantId tenant, String hash) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        public CompletionStage<Optional<OrganizationInvitation>> findPendingByTenantAndOrganizationAndEmail(
                TenantId tenant, OrganizationId organization, String email) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static final class SingleClaimOutbox implements OrganizationInvitationDeliveryOutboxPort {
        private final ClaimedDelivery claimed;
        private int sentCount;
        private String deadLetterCode;\n        private String retryCode;\n        private Instant retryAt;
        private SingleClaimOutbox(ClaimedDelivery claimed) { this.claimed = claimed; }
        public CompletionStage<Void> enqueue(DeliveryMessage message) { return CompletableFuture.completedFuture(null); }
        public CompletionStage<List<ClaimedDelivery>> claim(Instant now, Instant leaseUntil, int limit) {
            return CompletableFuture.completedFuture(List.of(claimed));
        }
        public CompletionStage<Boolean> markSent(UUID id, TenantId tenant, UUID lease, Instant at) {
            sentCount++; return CompletableFuture.completedFuture(true);
        }
        public CompletionStage<Boolean> retry(UUID id, TenantId tenant, UUID lease, Instant at, String code) {
            return CompletableFuture.completedFuture(true);
        }
        public CompletionStage<Boolean> deadLetter(UUID id, TenantId tenant, UUID lease, Instant at, String code) {
            deadLetterCode = code; return CompletableFuture.completedFuture(true);
        }
    }
}
