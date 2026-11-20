package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationInvitation;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.spi.OrganizationInvitationRepository;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Issues opaque invitation secrets and persists only their SHA-256 digests.
 * The returned secret is available once to a trusted delivery adapter; it must
 * never be logged or persisted by that adapter in plaintext.
 */
public final class OrganizationInvitationService {
    private static final Duration DEFAULT_LIFETIME = Duration.ofDays(7);
    private final OrganizationInvitationRepository repository;
    private final OrganizationInvitationPolicyPort policy;
    private final Clock clock;
    private final SecureRandom random;
    private final OrganizationInvitationIssuancePort issuance;
    private final InvitationSecretCipherPort cipher;

    public OrganizationInvitationService(OrganizationInvitationRepository repository,
            OrganizationInvitationPolicyPort policy, Clock clock, SecureRandom random) {
        this.repository = Objects.requireNonNull(repository);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        this.issuance = null;
        this.cipher = null;
    }

    /** Delivery-enabled composition: invitation and encrypted outbox row commit atomically. */
    public OrganizationInvitationService(OrganizationInvitationRepository repository,
            OrganizationInvitationPolicyPort policy, Clock clock, SecureRandom random,
            OrganizationInvitationIssuancePort issuance, InvitationSecretCipherPort cipher) {
        this.repository = Objects.requireNonNull(repository);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        this.issuance = Objects.requireNonNull(issuance);
        this.cipher = Objects.requireNonNull(cipher);
    }

    /**
     * Issues an invitation and queues its encrypted email delivery in the same
     * database transaction. Unlike issue(), this method never returns the bearer secret.
     */
    public CompletionStage<QueuedInvitation> issueAndQueue(TenantId tenantId, OrganizationId organizationId,
            String recipientEmail, OrganizationMembership.Role role, UserId actorId) {
        if (issuance == null || cipher == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Secure delivery composition is required"));
        }
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(organizationId);
        Objects.requireNonNull(actorId);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Recipient email is required"));
        }
        return policy.requireCanInvite(tenantId, organizationId, actorId).thenCompose(authorized -> {
            String normalizedEmail = recipientEmail.trim().toLowerCase(java.util.Locale.ROOT);
            return repository.findPendingByTenantAndOrganizationAndEmail(
                    tenantId, organizationId, normalizedEmail).thenCompose(existing -> {
                Instant now = clock.instant();
                if (existing.isPresent()) {
                    OrganizationInvitation prior = existing.get();
                    if (now.isBefore(prior.expiresAt())) return CompletableFuture.failedFuture(
                            new IllegalStateException("A pending invitation already exists for this recipient"));
                    prior.expire(now);
                    return repository.save(prior, tenantId).thenCompose(expired ->
                            createAndQueue(tenantId, organizationId, normalizedEmail, role, actorId, now));
                }
                return createAndQueue(tenantId, organizationId, normalizedEmail, role, actorId, now);
            });
        });
    }

    private CompletionStage<QueuedInvitation> createAndQueue(TenantId tenantId, OrganizationId organizationId,
            String email, OrganizationMembership.Role role, UserId actorId, Instant now) {
        GeneratedSecret generated = generateSecret();
        OrganizationInvitation invitation = OrganizationInvitation.issue(
                OrganizationInvitationId.generate(), tenantId, organizationId, email, role,
                actorId, generated.digest(), now.plus(DEFAULT_LIFETIME), now);
        InvitationSecretCipherPort.EncryptedSecret encrypted =
                cipher.encrypt(tenantId, invitation.getId(), generated.plaintext());
        var delivery = new OrganizationInvitationDeliveryOutboxPort.DeliveryMessage(
                java.util.UUID.randomUUID(), tenantId, invitation.getId(), email, encrypted, now);
        return issuance.persist(invitation, delivery, tenantId).thenApply(ignored ->
                new QueuedInvitation(invitation.getId(), invitation.expiresAt(), email, organizationId, role));
    }

    public CompletionStage<IssuedInvitation> issue(TenantId tenantId, OrganizationId organizationId,
            String recipientEmail, OrganizationMembership.Role role, UserId actorId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(organizationId);
        Objects.requireNonNull(actorId);
        return policy.requireCanInvite(tenantId, organizationId, actorId).thenCompose(authorized -> {
            String normalizedEmail = recipientEmail.trim().toLowerCase(java.util.Locale.ROOT);
            return repository.findPendingByTenantAndOrganizationAndEmail(
                    tenantId, organizationId, normalizedEmail).thenCompose(existing -> {
                Instant now = clock.instant();
                if (existing.isPresent()) {
                    OrganizationInvitation prior = existing.get();
                    if (now.isBefore(prior.expiresAt())) return CompletableFuture.failedFuture(
                            new IllegalStateException("A pending invitation already exists for this recipient"));
                    prior.expire(now);
                    return repository.save(prior, tenantId).thenCompose(expired ->
                            createInvitation(tenantId, organizationId, normalizedEmail, role, actorId, now));
                }
                return createInvitation(tenantId, organizationId, normalizedEmail, role, actorId, now);
            });
        });
    }

    /**
     * Rotates the invitation bearer digest and atomically queues a newly encrypted
     * delivery. Any older pending or leased delivery is superseded in that transaction.
     */
    public CompletionStage<QueuedInvitation> resendAndQueue(TenantId tenantId,
            OrganizationInvitationId invitationId, UserId actorId) {
        if (issuance == null || cipher == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Secure delivery composition is required"));
        }
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(invitationId);
        Objects.requireNonNull(actorId);
        return repository.findByTenantAndId(tenantId, invitationId).thenCompose(found -> {
            OrganizationInvitation invitation = found.orElseThrow(
                    () -> new IllegalArgumentException("Invitation not found"));
            return policy.requireCanInvite(tenantId, invitation.organizationId(), actorId)
                    .thenCompose(authorized -> {
                        Instant now = clock.instant();
                        int expectedVersion = invitation.getVersion();
                        GeneratedSecret generated = generateSecret();
                        invitation.rotate(generated.digest(), now.plus(DEFAULT_LIFETIME), now);
                        InvitationSecretCipherPort.EncryptedSecret encrypted =
                                cipher.encrypt(tenantId, invitation.getId(), generated.plaintext());
                        var delivery = new OrganizationInvitationDeliveryOutboxPort.DeliveryMessage(
                                java.util.UUID.randomUUID(), tenantId, invitation.getId(),
                                invitation.recipientEmail(), encrypted, now);
                        return issuance.rotateAndPersist(invitation, delivery, tenantId, expectedVersion, now)
                                .thenApply(ignored -> new QueuedInvitation(invitation.getId(), invitation.expiresAt(),
                                        invitation.recipientEmail(), invitation.organizationId(), invitation.role()));
                    });
        });
    }

    public CompletionStage<IssuedInvitation> resend(TenantId tenantId,
            OrganizationInvitationId invitationId, UserId actorId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(invitationId);
        Objects.requireNonNull(actorId);
        return repository.findByTenantAndId(tenantId, invitationId).thenCompose(found -> {
            OrganizationInvitation invitation = found.orElseThrow(
                    () -> new IllegalArgumentException("Invitation not found"));
            return policy.requireCanInvite(tenantId, invitation.organizationId(), actorId)
                    .thenCompose(authorized -> {
                        Instant now = clock.instant();
                        GeneratedSecret secret = generateSecret();
                        invitation.rotate(secret.digest(), now.plus(DEFAULT_LIFETIME), now);
                        return repository.save(invitation, tenantId).thenApply(saved ->
                                new IssuedInvitation(saved.getId(), secret.plaintext(), saved.expiresAt(),
                                        saved.recipientEmail(), saved.organizationId(), saved.role()));
                    });
        });
    }

    public CompletionStage<Void> revoke(TenantId tenantId,
            OrganizationInvitationId invitationId, UserId actorId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(invitationId);
        Objects.requireNonNull(actorId);
        return repository.findByTenantAndId(tenantId, invitationId).thenCompose(found -> {
            OrganizationInvitation invitation = found.orElseThrow(
                    () -> new IllegalArgumentException("Invitation not found"));
            return policy.requireCanInvite(tenantId, invitation.organizationId(), actorId)
                    .thenCompose(authorized -> {
                        invitation.revoke(clock.instant());
                        return repository.save(invitation, tenantId).thenApply(saved -> null);
                    });
        });
    }

    private CompletionStage<IssuedInvitation> createInvitation(TenantId tenantId, OrganizationId organizationId,
            String email, OrganizationMembership.Role role, UserId actorId, Instant now) {
        GeneratedSecret secret = generateSecret();
        OrganizationInvitation invitation = OrganizationInvitation.issue(
                OrganizationInvitationId.generate(), tenantId, organizationId, email, role,
                actorId, secret.digest(), now.plus(DEFAULT_LIFETIME), now);
        return repository.save(invitation, tenantId).thenApply(saved ->
                new IssuedInvitation(saved.getId(), secret.plaintext(), saved.expiresAt(),
                        saved.recipientEmail(), saved.organizationId(), saved.role()));
    }

    private GeneratedSecret generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new GeneratedSecret(plaintext, java.util.HexFormat.of().formatHex(digest));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record GeneratedSecret(String plaintext, String digest) { }

    public record IssuedInvitation(OrganizationInvitationId invitationId, String oneTimeSecret,
            Instant expiresAt, String recipientEmail, OrganizationId organizationId,
            OrganizationMembership.Role role) { }

    public record QueuedInvitation(OrganizationInvitationId invitationId, Instant expiresAt,
            String recipientEmail, OrganizationId organizationId, OrganizationMembership.Role role) { }
}
