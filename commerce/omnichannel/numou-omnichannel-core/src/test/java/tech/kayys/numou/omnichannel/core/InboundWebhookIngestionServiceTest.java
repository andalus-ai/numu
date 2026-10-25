package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InboundWebhookIngestionServiceTest {
    private static final ChannelContext CONTEXT = ChannelContext.of(
            TenantId.of("00000000-0000-0000-0000-000000000001"),
            null, ChannelType.MARKETPLACE, "account-1", "corr-1");
    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void verifiesClaimsPersistsAndTransitionsBeforeReturningVerified() {
        AtomicInteger calls = new AtomicInteger();
        InboundEventInbox inbox = (provider, envelope) -> {
            calls.incrementAndGet();
            return new InboxClaimResult(InboundEventKey.from(envelope, provider),
                    InboxClaimDisposition.ACCEPTED, Optional.empty());
        };
        InboundPayloadStore payloads = new PayloadStoreFake();
        InboundEventVerificationStore transitions = (key, digest, reference, at) ->
                new InboundVerificationResult(InboundVerificationDisposition.VERIFIED, Optional.of(reference));
        var service = service(WebhookVerificationResult.success(), inbox, payloads, transitions);
        var result = service.ingest(request("event-1", "order.created", "body"));

        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.VERIFIED);
        assertThat(result.payloadReference()).isPresent();
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsBeforeClaimingWhenSignatureVerificationFails() {
        AtomicInteger claims = new AtomicInteger();
        InboundEventInbox inbox = (provider, envelope) -> {
            claims.incrementAndGet();
            return new InboxClaimResult(InboundEventKey.from(envelope, provider),
                    InboxClaimDisposition.ACCEPTED, Optional.empty());
        };
        var service = service(WebhookVerificationResult.rejected("INVALID_SIGNATURE"), inbox,
                new PayloadStoreFake(), (key, digest, ref, at) ->
                        new InboundVerificationResult(InboundVerificationDisposition.VERIFIED, Optional.of(ref)));

        var result = service.ingest(request("event-1", "order.created", "body"));
        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.REJECTED);
        assertThat(result.reasonCode()).isEqualTo("INVALID_SIGNATURE");
        assertThat(claims).hasValue(0);
    }

    @Test
    void verifierExceptionFailsClosedWithoutClaimingOrPersisting() {
        AtomicInteger claims = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        ProviderWebhookVerifier verifier = new ProviderWebhookVerifier() {
            @Override public String providerId() { return "test-provider"; }
            @Override public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) {
                throw new IllegalStateException("sensitive provider detail");
            }
        };
        InboundPayloadStore payloads = new PayloadStoreFake() {
            @Override public InboundPayloadReference store(ChannelContext c, byte[] b, String h, Instant expiry) {
                writes.incrementAndGet();
                return super.store(c, b, h, expiry);
            }
        };
        var service = new InboundWebhookIngestionService(List.of(verifier),
                (provider, envelope) -> {
                    claims.incrementAndGet();
                    return new InboxClaimResult(InboundEventKey.from(envelope, provider),
                            InboxClaimDisposition.ACCEPTED, Optional.empty());
                }, payloads,
                (key, digest, ref, at) -> new InboundVerificationResult(
                        InboundVerificationDisposition.VERIFIED, Optional.of(ref)),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));

        var result = service.ingest(request("event-1", "order.created", "body"));

        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.REJECTED);
        assertThat(result.reasonCode()).isEqualTo("VERIFICATION_FAILED");
        assertThat(result.reasonCode()).doesNotContain("sensitive provider detail");
        assertThat(result.payloadReference()).isEmpty();
        assertThat(claims).hasValue(0);
        assertThat(writes).hasValue(0);
    }

    @Test
    void duplicateDoesNotPersistSecondPayload() {
        AtomicInteger writes = new AtomicInteger();
        InboundEventInbox inbox = (provider, envelope) -> new InboxClaimResult(
                InboundEventKey.from(envelope, provider), InboxClaimDisposition.DUPLICATE,
                Optional.of(envelope.payloadSha256()));
        InboundPayloadStore payloads = new PayloadStoreFake() {
            @Override public InboundPayloadReference store(ChannelContext c, byte[] b, String h, Instant expiry) {
                writes.incrementAndGet();
                return super.store(c, b, h, expiry);
            }
        };
        var service = service(WebhookVerificationResult.success(), inbox, payloads,
                (key, digest, ref, at) -> new InboundVerificationResult(
                        InboundVerificationDisposition.VERIFIED, Optional.of(ref)));

        var result = service.ingest(request("event-1", "order.created", "body"));
        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.DUPLICATE);
        assertThat(writes).hasValue(0);
    }

    @Test
    void cleansUpPayloadWhenAtomicTransitionFails() {
        PayloadStoreFake payloads = new PayloadStoreFake();
        var service = service(WebhookVerificationResult.success(),
                (provider, envelope) -> new InboxClaimResult(InboundEventKey.from(envelope, provider),
                        InboxClaimDisposition.ACCEPTED, Optional.empty()),
                payloads,
                (key, digest, ref, at) -> new InboundVerificationResult(
                        InboundVerificationDisposition.DIGEST_MISMATCH, Optional.empty()));

        var result = service.ingest(request("event-1", "order.created", "body"));
        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.VERIFICATION_TRANSITION_FAILED);
        assertThat(payloads.deleted).isEqualTo(1);
    }

    @Test
    void rejectsOversizedPayloadBeforeVerificationOrPersistence() {
        AtomicInteger verifications = new AtomicInteger();
        AtomicInteger claims = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        ProviderWebhookVerifier verifier = new ProviderWebhookVerifier() {
            @Override public String providerId() { return "test-provider"; }
            @Override public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) {
                verifications.incrementAndGet();
                return WebhookVerificationResult.success();
            }
        };
        InboundPayloadStore payloads = new PayloadStoreFake() {
            @Override public InboundPayloadReference store(ChannelContext c, byte[] b, String h, Instant expiry) {
                writes.incrementAndGet();
                return super.store(c, b, h, expiry);
            }
        };
        var service = new InboundWebhookIngestionService(List.of(verifier),
                (provider, envelope) -> {
                    claims.incrementAndGet();
                    return new InboxClaimResult(InboundEventKey.from(envelope, provider),
                            InboxClaimDisposition.ACCEPTED, Optional.empty());
                }, payloads,
                (key, digest, ref, at) -> new InboundVerificationResult(
                        InboundVerificationDisposition.VERIFIED, Optional.of(ref)),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));

        byte[] oversizedBody = new byte[InboundWebhookIngestionService.DEFAULT_MAX_PAYLOAD_BYTES + 1];
        var oversizedRequest = new InboundWebhookRequest(CONTEXT, "test-provider", Map.of(),
                oversizedBody, "event-large", "order.created", Optional.empty());

        var result = service.ingest(oversizedRequest);

        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.PAYLOAD_TOO_LARGE);
        assertThat(result.reasonCode()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(result.payloadReference()).isEmpty();
        assertThat(verifications).hasValue(0);
        assertThat(claims).hasValue(0);
        assertThat(writes).hasValue(0);
    }

    private static InboundWebhookIngestionService service(
            WebhookVerificationResult verification,
            InboundEventInbox inbox,
            InboundPayloadStore payloads,
            InboundEventVerificationStore transitions) {
        ProviderWebhookVerifier verifier = new ProviderWebhookVerifier() {
            @Override public String providerId() { return "test-provider"; }
            @Override public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) { return verification; }
        };
        return new InboundWebhookIngestionService(List.of(verifier), inbox, payloads, transitions,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));
    }

    private static InboundWebhookRequest request(String id, String type, String body) {
        return new InboundWebhookRequest(CONTEXT, "test-provider", Map.of("x-test", "value"),
                body.getBytes(StandardCharsets.UTF_8), id, type, Optional.empty());
    }

    private static class PayloadStoreFake implements InboundPayloadStore {
        int deleted;
        @Override public InboundPayloadReference store(ChannelContext context, byte[] payload, String digest, Instant expiry) {
            return new InboundPayloadReference("00000000-0000-0000-0000-000000000123");
        }
        @Override public Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference) { return Optional.empty(); }
        @Override public boolean delete(ChannelContext context, InboundPayloadReference reference) { deleted++; return true; }
    }
}
