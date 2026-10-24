package tech.kayys.numou.omnichannel.core;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coordinates signature verification, durable inbox deduplication, payload
 * persistence, and the durable RECEIVED-to-VERIFIED transition. Business
 * dispatch is deliberately left to a leased worker.
 */
public final class InboundWebhookIngestionService {
    private final Map<String, ProviderWebhookVerifier> verifiers;
    private final InboundEventInbox inbox;
    private final InboundPayloadStore payloadStore;
    private final InboundEventVerificationStore verificationStore;
    private final Clock clock;
    private final Duration payloadRetention;

    /**
     * Preferred constructor when provider capabilities are available: the
     * registry validates declarations before the ingestion service is wired.
     */
    public InboundWebhookIngestionService(
            ProviderWebhookAdapterRegistry registry,
            InboundEventInbox inbox,
            InboundPayloadStore payloadStore,
            InboundEventVerificationStore verificationStore,
            Clock clock,
            Duration payloadRetention) {
        this(Objects.requireNonNull(registry, "registry must not be null").verifiers(),
                inbox, payloadStore, verificationStore, clock, payloadRetention);
    }

    public InboundWebhookIngestionService(
            List<ProviderWebhookVerifier> verifiers,
            InboundEventInbox inbox,
            InboundPayloadStore payloadStore,
            InboundEventVerificationStore verificationStore,
            Clock clock,
            Duration payloadRetention) {
        Objects.requireNonNull(verifiers, "verifiers must not be null");
        this.verifiers = verifiers.stream().collect(Collectors.toUnmodifiableMap(
                verifier -> requiredProviderId(verifier.providerId()), Function.identity()));
        this.inbox = Objects.requireNonNull(inbox, "inbox must not be null");
        this.payloadStore = Objects.requireNonNull(payloadStore, "payloadStore must not be null");
        this.verificationStore = Objects.requireNonNull(verificationStore, "verificationStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.payloadRetention = Objects.requireNonNull(payloadRetention, "payloadRetention must not be null");
        if (payloadRetention.isZero() || payloadRetention.isNegative()) {
            throw new IllegalArgumentException("payloadRetention must be positive");
        }
    }

    public InboundIngestionResult ingest(InboundWebhookRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ProviderWebhookVerifier verifier = verifiers.get(request.providerId());
        if (verifier == null) {
            return result(InboundIngestionDisposition.REJECTED, "UNSUPPORTED_PROVIDER", null);
        }

        byte[] body = request.rawBody();
        String digest = sha256(body);
        WebhookVerificationResult verification = verifier.verify(new ProviderWebhookVerificationRequest(
                request.providerId(), request.context().providerAccountId(), request.headers(), body));
        if (!verification.verified()) {
            return result(InboundIngestionDisposition.REJECTED, verification.reasonCode(), null);
        }

        Instant receivedAt = clock.instant();
        InboundEventEnvelope envelope = new InboundEventEnvelope(
                request.context(), request.externalEventId(), request.eventType(), digest,
                receivedAt, request.providerOccurredAt());
        InboxClaimResult claim = inbox.claim(request.providerId(), envelope);
        if (claim.disposition() == InboxClaimDisposition.DUPLICATE) {
            return result(InboundIngestionDisposition.DUPLICATE, "DUPLICATE_EVENT", null);
        }
        if (claim.disposition() == InboxClaimDisposition.PAYLOAD_CONFLICT) {
            return result(InboundIngestionDisposition.PAYLOAD_CONFLICT, "EVENT_ID_PAYLOAD_CONFLICT", null);
        }

        InboundPayloadReference reference;
        try {
            reference = payloadStore.store(request.context(), body, digest, receivedAt.plus(payloadRetention));
        } catch (RuntimeException exception) {
            return result(InboundIngestionDisposition.PAYLOAD_STORAGE_FAILED, "PAYLOAD_STORAGE_FAILED", null);
        }

        InboundVerificationResult transition;
        try {
            transition = verificationStore.markVerified(claim.key(), digest, reference, receivedAt);
        } catch (RuntimeException exception) {
            deleteQuietly(request.context(), reference);
            return result(InboundIngestionDisposition.VERIFICATION_TRANSITION_FAILED,
                    "VERIFICATION_TRANSITION_FAILED", null);
        }
        if (!transition.isVerified()) {
            deleteQuietly(request.context(), reference);
            return result(InboundIngestionDisposition.VERIFICATION_TRANSITION_FAILED,
                    transition.disposition().name(), null);
        }
        return result(InboundIngestionDisposition.VERIFIED, "VERIFIED", reference);
    }

    private void deleteQuietly(ChannelContext context, InboundPayloadReference reference) {
        try {
            payloadStore.delete(context, reference);
        } catch (RuntimeException ignored) {
            // Expiry cleanup is the backstop; preserve the primary failure result.
        }
    }

    private static InboundIngestionResult result(
            InboundIngestionDisposition disposition, String reason, InboundPayloadReference reference) {
        return new InboundIngestionResult(disposition, reason,
                java.util.Optional.ofNullable(reference));
    }

    private static String requiredProviderId(String value) {
        Objects.requireNonNull(value, "verifier providerId must not be null");
        String id = value.trim();
        if (id.isEmpty() || id.length() > 100) {
            throw new IllegalArgumentException("verifier providerId must contain 1 to 100 characters");
        }
        return id;
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
