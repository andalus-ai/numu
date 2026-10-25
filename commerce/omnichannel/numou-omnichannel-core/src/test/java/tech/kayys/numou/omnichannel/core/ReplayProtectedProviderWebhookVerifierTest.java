package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayProtectedProviderWebhookVerifierTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    @Test
    void reservesVerifiedReplayKeyOnceAndRejectsRedelivery() {
        Set<String> keys = new HashSet<>();
        WebhookReplayStore store = reservation -> keys.add(
                reservation.providerId() + ":" + reservation.providerAccountId() + ":" + reservation.replayKeySha256());
        var verifier = verifier(true, store, request -> Optional.of("provider-event-42"));

        var first = verifier.verify(request());
        var second = verifier.verify(request());

        assertThat(first.verified()).isTrue();
        assertThat(second.verified()).isFalse();
        assertThat(second.reasonCode()).isEqualTo("REPLAY_DETECTED");
        assertThat(keys).hasSize(1);
    }

    @Test
    void doesNotReserveRejectedSignature() {
        Set<String> keys = new HashSet<>();
        WebhookReplayStore store = reservation -> { keys.add(reservation.replayKeySha256()); return true; };
        var verifier = verifier(false, store, request -> Optional.of("provider-event-42"));

        var result = verifier.verify(request());

        assertThat(result.verified()).isFalse();
        assertThat(keys).isEmpty();
    }

    @Test
    void failsClosedWhenReplayStoreIsUnavailable() {
        var verifier = verifier(true, reservation -> { throw new IllegalStateException("database down"); },
                request -> Optional.of("provider-event-42"));

        var result = verifier.verify(request());

        assertThat(result.verified()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("REPLAY_PROTECTION_UNAVAILABLE");
    }

    @Test
    void failsClosedWhenSignedReplayKeyCannotBeExtracted() {
        var verifier = verifier(true, reservation -> true, request -> Optional.empty());

        var result = verifier.verify(request());

        assertThat(result.verified()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("REPLAY_KEY_UNAVAILABLE");
    }

    private static ReplayProtectedProviderWebhookVerifier verifier(
            boolean signatureValid, WebhookReplayStore store, ProviderWebhookReplayKeyExtractor extractor) {
        ProviderWebhookVerifier delegate = new ProviderWebhookVerifier() {
            @Override public String providerId() { return "test-provider"; }
            @Override public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) {
                return signatureValid ? WebhookVerificationResult.success()
                        : WebhookVerificationResult.rejected("INVALID_SIGNATURE");
            }
            @Override public boolean validatesProviderTimestamp() { return true; }
        };
        return new ReplayProtectedProviderWebhookVerifier(delegate, extractor, store,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));
    }

    private static ProviderWebhookVerificationRequest request() {
        return new ProviderWebhookVerificationRequest("test-provider", "account-1", Map.of(), new byte[] {1, 2, 3});
    }
}
