package tech.kayys.numou.organization.application;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Scheduler-invoked worker; creates no background threads. */
public final class OrganizationInvitationDeliveryWorker {
    private final OrganizationInvitationDeliveryOutboxPort outbox;
    private final InvitationSecretCipherPort cipher;
    private final InvitationEmailProvider provider;
    private final Clock clock;
    private final URI publicBaseUri;
    private final Duration leaseDuration;

    public OrganizationInvitationDeliveryWorker(OrganizationInvitationDeliveryOutboxPort outbox,
            InvitationSecretCipherPort cipher, InvitationEmailProvider provider, Clock clock,
            URI publicBaseUri, Duration leaseDuration) {
        this.outbox = Objects.requireNonNull(outbox);
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
        return deliver(item).handle((ignored, failure) -> failure).thenCompose(failure -> {
            if (failure == null) return outbox.markSent(item.deliveryId(), item.tenantId(),
                    item.leaseToken(), clock.instant()).thenApply(changed -> null);
            String code = failure instanceof SecurityException ? "SECRET_DECRYPTION_FAILED" : "EMAIL_PROVIDER_FAILED";
            if (item.attempts() >= 8) return outbox.deadLetter(item.deliveryId(), item.tenantId(),
                    item.leaseToken(), clock.instant(), code).thenApply(changed -> null);
            long delay = Math.min(3600L, 30L << Math.min(Math.max(0, item.attempts() - 1), 7));
            return outbox.retry(item.deliveryId(), item.tenantId(), item.leaseToken(),
                    clock.instant().plusSeconds(delay), code).thenApply(changed -> null);
        }).thenCompose(ignored -> process(items, index + 1));
    }

    private CompletionStage<Void> deliver(OrganizationInvitationDeliveryOutboxPort.ClaimedDelivery item) {
        String secret = cipher.decrypt(item.tenantId(), item.invitationId(), item.secret());
        String url = publicBaseUri.toString() + "/invitations/accept?token=" + secret;
        return provider.send(new InvitationEmailProvider.Email(item.deliveryId(), item.recipientEmail(), url));
    }
}
