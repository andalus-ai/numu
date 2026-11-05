package tech.kayys.numou.organization.application;

import tech.kayys.numou.organization.domain.OrganizationInvitation;
import tech.kayys.numou.organization.domain.OrganizationInvitationStatus;
import tech.kayys.numou.organization.spi.OrganizationInvitationRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Scheduler-invoked worker; creates no background threads. */
public final class OrganizationInvitationDeliveryWorker {
    private final OrganizationInvitationDeliveryOutboxPort outbox;
    private final OrganizationInvitationRepository invitations;
    private final InvitationSecretCipherPort cipher;
    private final InvitationEmailProvider provider;
    private final Clock clock;
    private final URI publicBaseUri;
    private final Duration leaseDuration;

    public OrganizationInvitationDeliveryWorker(OrganizationInvitationDeliveryOutboxPort outbox,
            OrganizationInvitationRepository invitations, InvitationSecretCipherPort cipher,
            InvitationEmailProvider provider, Clock clock, URI publicBaseUri, Duration leaseDuration) {
        this.outbox = Objects.requireNonNull(outbox);
        this.invitations = Objects.requireNonNull(invitations);
        this.cipher = Objects.requireNonNull(cipher);
        this.provider = Objects.requireNonNull(provider);
        this.clock = Objects.requireNonNull(clock);
        this.publicBaseUri = Objects.requireNonNull(publicBaseUri);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        if (!"https".equalsIgnoreCase(publicBaseUri.getScheme()) || publicBaseUri.getHost() == null
                || leaseDuration.isZero() || leaseDuration.isNegative())
            throw new IllegalArgumentException("HTTPS URL and positive lease duration are required");
    }

    public CompletionStage<Integer> runBatch(int limit) {
        if (limit < 1 || limit > 100)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Batch limit must be 1..100"));
        Instant now = clock.instant();
        return outbox.claim(now, now.plus(leaseDuration), limit).thenCompose(items ->
                process(items, 0).thenApply(ignored -> items.size()));
    }

    private CompletionStage<Void> process(List<OrganizationInvitationDeliveryOutboxPort.ClaimedDelivery> items,
                                           int index) {
        if (index >= items.size()) return CompletableFuture.completedFuture(null);
        var item = items.get(index);
        return deliverIfCurrent(item).handle((sent, failure) -> new Outcome(sent != null && sent, failure))
                .thenCompose(outcome -> {
                    if (outcome.failure() == null && outcome.sent()) {
                        return outbox.markSent(item.deliveryId(), item.tenantId(), item.leaseToken(), clock.instant())
                                .thenApply(changed -> null);
                    }
                    if (outcome.failure() == null) {
                        return outbox.deadLetter(item.deliveryId(), item.tenantId(), item.leaseToken(),
                                clock.instant(), "INVITATION_NOT_CURRENT").thenApply(changed -> null);
                    }
                    String code = unwrap(outcome.failure()) instanceof SecurityException
                            ? "SECRET_DECRYPTION_FAILED" : "EMAIL_PROVIDER_FAILED";
                    if (item.attempts() >= 8) {
                        return outbox.deadLetter(item.deliveryId(), item.tenantId(), item.leaseToken(),
                                clock.instant(), code).thenApply(changed -> null);
                    }
                    long delay = Math.min(3600L, 30L << Math.min(Math.max(0, item.attempts() - 1), 7));
                    return outbox.retry(item.deliveryId(), item.tenantId(), item.leaseToken(),
                            clock.instant().plusSeconds(delay), code).thenApply(changed -> null);
                }).thenCompose(ignored -> process(items, index + 1));
    }

    private CompletionStage<Boolean> deliverIfCurrent(OrganizationInvitationDeliveryOutboxPort.ClaimedDelivery item) {
        return invitations.findByTenantAndId(item.tenantId(), item.invitationId()).thenCompose(found -> {
            Optional<OrganizationInvitation> maybe = found;
            if (maybe.isEmpty()) return CompletableFuture.completedFuture(false);
            OrganizationInvitation invitation = maybe.get();
            Instant now = clock.instant();
            if (invitation.status() != OrganizationInvitationStatus.PENDING || !invitation.expiresAt().isAfter(now))
                return CompletableFuture.completedFuture(false);

            String secret = cipher.decrypt(item.tenantId(), item.invitationId(), item.secret());
            if (!constantTimeDigestMatch(secret, invitation.tokenHash()))
                return CompletableFuture.completedFuture(false);
            String base = publicBaseUri.toString().replaceAll("/+$", "");
            String url = base + "/invitations/accept?token=" + secret;
            return provider.send(new InvitationEmailProvider.Email(
                    item.deliveryId(), item.recipientEmail(), url)).thenApply(ignored -> true);
        });
    }

    private static boolean constantTimeDigestMatch(String secret, String expectedHex) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            byte[] expected = expectedHex.getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(HexFormat.of().formatHex(digest).getBytes(StandardCharsets.US_ASCII), expected);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null)
            current = current.getCause();
        return current;
    }

    private record Outcome(boolean sent, Throwable failure) { }
}
