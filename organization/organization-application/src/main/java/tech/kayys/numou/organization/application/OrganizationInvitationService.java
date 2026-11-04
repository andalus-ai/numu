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

    public OrganizationInvitationService(OrganizationInvitationRepository repository,
            OrganizationInvitationPolicyPort policy, Clock clock, SecureRandom random) {
        this.repository = Objects.requireNonNull(repository);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }

    public CompletionStage<IssuedInvitation> issue(TenantId tenantId, OrganizationId organizationId,
            String recipientEmail, OrganizationMembership.Role role, UserId actorId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(organizationId);
        Objects.requireNonNull(actorId);
        return policy.requireCanInvite(tenantId, organizationId, actorId).thenCompose(ignored -> {
            String normalizedEmail = recipientEmail.trim().toLowerCase(java.util.Locale.ROOT);
            return repository.findPendingByTenantAndOrganizationAndEmail(
                    tenantId, organizationId, normalizedEmail).thenCompose(existing -> {
                if (existing.isPresent()) return CompletableFuture.failedFuture(
                        new IllegalStateException("A pending invitation already exists for this recipient"));
                Instant now = clock.instant();
                GeneratedSecret secret = generateSecret();
                OrganizationInvitation invitation = OrganizationInvitation.issue(
                        OrganizationInvitationId.generate(), tenantId, organizationId, normalizedEmail, role,
                        actorId, secret.digest(), now.plus(DEFAULT_LIFETIME), now);
                return repository.save(invitation, tenantId).thenApply(saved ->
                        new IssuedInvitation(saved.getId(), secret.plaintext(), saved.expiresAt(),
                                saved.recipientEmail(), saved.organizationId(), saved.role()));
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
                    .thenCompose(ignored -> {
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
                    .thenCompose(ignored -> {
                        invitation.revoke(clock.instant());
                        return repository.save(invitation, tenantId).thenApply(saved -> null);
                    });
        });
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
}