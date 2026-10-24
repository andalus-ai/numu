package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InboundWebhookIngestionReliabilityTest {
    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");
    private static final String PROVIDER = "test-provider";
    private static final ChannelContext CONTEXT = ChannelContext.of(
            TenantId.of("00000000-0000-0000-0000-000000000001"),
            null, ChannelType.MARKETPLACE, "account-1", "corr-1");

    @Test
    void unknownProviderIsRejectedWithoutClaimOrPayloadWrite() {
        AtomicInteger claims = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        InboundWebhookIngestionService service = service(List.of(),
                (provider, envelope) -> { claims.incrementAndGet(); return accepted(provider, envelope); },
                store(writes, new AtomicInteger()),
                (key, digest, ref, at) -> verified(ref));

        InboundIngestionResult result = service.ingest(request("event-1", "body"));

        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.REJECTED);
        assertThat(result.reasonCode()).isEqualTo("UNSUPPORTED_PROVIDER");
        assertThat(claims).hasValue(0);
        assertThat(writes).hasValue(0);
    }

    @Test
    void duplicateAndPayloadConflictNeverWriteOrTransition() {
        for (InboxClaimDisposition disposition : List.of(
                InboxClaimDisposition.DUPLICATE, InboxClaimDisposition.PAYLOAD_CONFLICT)) {
            AtomicInteger writes = new AtomicInteger();
            AtomicInteger transitions = new AtomicInteger();
            InboundEventInbox inbox = (provider, envelope) -> new InboxClaimResult(
                    InboundEventKey.from(envelope, provider), disposition,
                    Optional.of(envelope.payloadSha256()));
            InboundWebhookIngestionService service = service(List.of(verifier(true)),
                    inbox, store(writes, new AtomicInteger()),
                    (key, digest, ref, at) -> {
                        transitions.incrementAndGet();
                        return verified(ref);
                    });

            InboundIngestionResult result = service.ingest(request("event-1", "body"));

            assertThat(result.disposition()).isEqualTo(disposition == InboxClaimDisposition.DUPLICATE
                    ? InboundIngestionDisposition.DUPLICATE : InboundIngestionDisposition.PAYLOAD_CONFLICT);
            assertThat(writes).hasValue(0);
            assertThat(transitions).hasValue(0);
            assertThat(result.payloadReference()).isEmpty();
        }
    }

    @Test
    void payloadStorageFailureDoesNotAttemptVerifiedTransition() {
        AtomicInteger transitions = new AtomicInteger();
        InboundPayloadStore brokenStore = new InboundPayloadStore() {
            @Override public InboundPayloadReference store(ChannelContext context, byte[] payload,
                    String expectedSha256, Instant retentionUntil) {
                throw new IllegalStateException("private storage detail");
            }
            @Override public Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference) {
                return Optional.empty();
            }
            @Override public boolean delete(ChannelContext context, InboundPayloadReference reference) {
                return false;
            }
        };
        InboundWebhookIngestionService service = service(List.of(verifier(true)),
                (provider, envelope) -> accepted(provider, envelope), brokenStore,
                (key, digest, ref, at) -> {
                    transitions.incrementAndGet();
                    return verified(ref);
                });

        InboundIngestionResult result = service.ingest(request("event-1", "body"));

        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.PAYLOAD_STORAGE_FAILED);
        assertThat(result.reasonCode()).isEqualTo("PAYLOAD_STORAGE_FAILED");
        assertThat(result.reasonCode()).doesNotContain("private storage detail");
        assertThat(transitions).hasValue(0);
    }

    @Test
    void eventKeyScopesSameProviderEventIdByTenantAndProviderAccount() {
        ChannelContext otherTenant = ChannelContext.of(
                TenantId.of("00000000-0000-0000-0000-000000000002"),
                null, ChannelType.MARKETPLACE, "account-1", "corr-2");
        ChannelContext otherAccount = ChannelContext.of(
                CONTEXT.tenantId(), null, ChannelType.MARKETPLACE, "account-2", "corr-3");
        InboundEventEnvelope first = envelope(CONTEXT, "event-same", "body");
        InboundEventEnvelope secondTenant = envelope(otherTenant, "event-same", "body");
        InboundEventEnvelope secondAccount = envelope(otherAccount, "event-same", "body");

        assertThat(InboundEventKey.from(first, PROVIDER))
                .isNotEqualTo(InboundEventKey.from(secondTenant, PROVIDER));
        assertThat(InboundEventKey.from(first, PROVIDER))
                .isNotEqualTo(InboundEventKey.from(secondAccount, PROVIDER));
    }

    @Test
    void successfulPathUsesRawBodyDigestAndBoundedRetentionDeadline() throws Exception {
        AtomicInteger transitions = new AtomicInteger();
        InboundPayloadStore payloads = new InboundPayloadStore() {
            @Override public InboundPayloadReference store(ChannelContext context, byte[] payload,
                    String expectedSha256, Instant retentionUntil) {
                assertThat(new String(payload, StandardCharsets.UTF_8)).isEqualTo("body");
                assertThat(expectedSha256).isEqualTo(sha256(payload));
                assertThat(retentionUntil).isEqualTo(NOW.plus(Duration.ofHours(1)));
                return new InboundPayloadReference("opaque-ref");
            }
            @Override public Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference) {
                return Optional.empty();
            }
            @Override public boolean delete(ChannelContext context, InboundPayloadReference reference) {
                return false;
            }
        };
        InboundWebhookIngestionService service = service(List.of(verifier(true)),
                (provider, envelope) -> {
                    assertThat(envelope.payloadSha256()).isEqualTo(sha256("body".getBytes(StandardCharsets.UTF_8)));
                    return accepted(provider, envelope);
                }, payloads,
                (key, digest, reference, at) -> {
                    transitions.incrementAndGet();
                    assertThat(key.tenantId()).isEqualTo(CONTEXT.tenantId());
                    assertThat(key.providerAccountId()).isEqualTo("account-1");
                    assertThat(digest).isEqualTo(sha256("body".getBytes(StandardCharsets.UTF_8)));
                    assertThat(reference.value()).isEqualTo("opaque-ref");
                    assertThat(at).isEqualTo(NOW);
                    return verified(reference);
                });

        InboundIngestionResult result = service.ingest(request("event-1", "body"));

        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.VERIFIED);
        assertThat(result.payloadReference()).contains(new InboundPayloadReference("opaque-ref"));
        assertThat(transitions).hasValue(1);
    }

    private static InboundWebhookIngestionService service(List<ProviderWebhookVerifier> verifiers,
            InboundEventInbox inbox, InboundPayloadStore payloads,
            InboundEventVerificationStore verificationStore) {
        return new InboundWebhookIngestionService(verifiers, inbox, payloads, verificationStore,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));
    }

    private static ProviderWebhookVerifier verifier(boolean valid) {
        return new ProviderWebhookVerifier() {
            @Override public String providerId() { return PROVIDER; }
            @Override public WebhookVerificationResult verify(ProviderWebhookVerificationRequest request) {
                assertThat(request.providerId()).isEqualTo(PROVIDER);
                assertThat(request.providerAccountId()).isEqualTo("account-1");
                assertThat(request.rawBody()).isNotEmpty();
                return valid ? WebhookVerificationResult.success()
                        : WebhookVerificationResult.rejected("INVALID_SIGNATURE");
            }
        };
    }

    private static InboundWebhookRequest request(String id, String body) {
        return new InboundWebhookRequest(CONTEXT, PROVIDER, Map.of("x-test", "signature"),
                body.getBytes(StandardCharsets.UTF_8), id, "order.updated", Optional.empty());
    }

    private static InboundEventEnvelope envelope(ChannelContext context, String id, String body) {
        return new InboundEventEnvelope(context, id, "order.updated",
                sha256(body.getBytes(StandardCharsets.UTF_8)), NOW, Optional.empty());
    }

    private static InboxClaimResult accepted(String provider, InboundEventEnvelope envelope) {
        return new InboxClaimResult(InboundEventKey.from(envelope, provider),
                InboxClaimDisposition.ACCEPTED, Optional.empty());
    }

    private static InboundVerificationResult verified(InboundPayloadReference reference) {
        return new InboundVerificationResult(InboundVerificationDisposition.VERIFIED, Optional.of(reference));
    }

    private static InboundPayloadStore store(AtomicInteger writes, AtomicInteger deletes) {
        return new InboundPayloadStore() {
            @Override public InboundPayloadReference store(ChannelContext context, byte[] payload,
                    String digest, Instant expiry) {
                writes.incrementAndGet();
                return new InboundPayloadReference("opaque-ref");
            }
            @Override public Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference) {
                return Optional.empty();
            }
            @Override public boolean delete(ChannelContext context, InboundPayloadReference reference) {
                deletes.incrementAndGet();
                return true;
            }
        };
    }

    private static String sha256(byte[] body) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
