package tech.kayys.numou.omnichannel.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorates a provider verifier with atomic replay reservation. Provider-specific
 * verifiers remain responsible for signature and timestamp validation; this
 * decorator reserves a replay key only after authenticity verification succeeds.
 */
public final class ReplayProtectedProviderWebhookVerifier implements ProviderWebhookVerifier {
    private final ProviderWebhookVerifier delegate;
    private final ProviderWebhookReplayKeyExtractor keyExtractor;
    private final WebhookReplayStore replayStore;
    private final Clock clock;
    private final Duration retention;

    public ReplayProtectedProviderWebhookVerifier(
            ProviderWebhookVerifier delegate,
            ProviderWebhookReplayKeyExtractor keyExtractor,
            WebhookReplayStore replayStore,
            Clock clock,
            Duration retention) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        this.replayStore = Objects.requireNonNull(replayStore, "replayStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.retention = Objects.requireNonNull(retention, "retention must not be null");
        if (retention.isZero() || retention.isNegative()) throw new IllegalArgumentException("retention must be positive");
    }

    @Override public String providerId() { return delegate.providerId(); }
    @Override public boolean validatesProviderTimestamp() { return delegate.validatesProviderTimestamp(); }
    @Override public boolean enforcesReplayProtection() { return true; }

    @Override
    public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) {
        WebhookVerificationResult verified = delegate.verify(request);
        if (verified == null || !verified.verified()) return verified == null
                ? WebhookVerificationResult.rejected("VERIFICATION_UNAVAILABLE") : verified;
        try {
            Optional<String> extracted = keyExtractor.extract(request);
            if (extracted == null || extracted.isEmpty() || extracted.get().isBlank()) {
                return WebhookVerificationResult.rejected("REPLAY_KEY_UNAVAILABLE");
            }
            String replayKeyHash = sha256(extracted.get());
            var now = clock.instant();
            boolean reserved = replayStore.reserve(new WebhookReplayReservation(
                    providerId(), request.providerAccountId(), replayKeyHash, now, now.plus(retention)));
            return reserved ? verified : WebhookVerificationResult.rejected("REPLAY_DETECTED");
        } catch (RuntimeException exception) {
            return WebhookVerificationResult.rejected("REPLAY_PROTECTION_UNAVAILABLE");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}