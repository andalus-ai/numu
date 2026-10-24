package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundEventWorkerTest {
    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");
    private static final byte[] BODY = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final String DIGEST = "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a";
    private static final InboundPayloadReference REFERENCE = new InboundPayloadReference("opaque-payload-1");

    @Test
    void loadsTenantScopedPayloadDispatchesAndFencesCompletion() {
        Fixture f = new Fixture();
        AtomicReference<VerifiedInboundEvent> dispatched = new AtomicReference<>();
        InboundEventWorker worker = f.worker(dispatched::set);

        assertThat(worker.processNext()).isEqualTo(InboundWorkerDisposition.PROCESSED);
        assertThat(dispatched.get()).isNotNull();
        assertThat(dispatched.get().providerId()).isEqualTo("provider");
        assertThat(dispatched.get().envelope().externalEventId()).isEqualTo("event-1");
        assertThat(dispatched.get().payloadReference()).isEqualTo(REFERENCE);
        assertThat(f.processed).isTrue();
        assertThat(f.failureCode.get()).isNull();
    }

    @Test
    void digestMismatchIsNeverDispatchedAndSchedulesRetry() {
        Fixture f = new Fixture();
        f.item = f.item("0".repeat(64));
        AtomicReference<Boolean> dispatched = new AtomicReference<>(false);
        InboundEventWorker worker = f.worker(event -> dispatched.set(true));

        assertThat(worker.processNext()).isEqualTo(InboundWorkerDisposition.RETRY_SCHEDULED);
        assertThat(dispatched.get()).isFalse();
        assertThat(f.failureCode.get()).isEqualTo("PAYLOAD_DIGEST_MISMATCH");
        assertThat(f.processed).isFalse();
    }

    @Test
    void dispatcherFailureUsesSanitizedCodeAndRetries() {
        Fixture f = new Fixture();
        InboundEventWorker worker = f.worker(event -> { throw new IllegalStateException("secret payload must not leak"); });

        assertThat(worker.processNext()).isEqualTo(InboundWorkerDisposition.RETRY_SCHEDULED);
        assertThat(f.failureCode.get()).isEqualTo("INBOUND_DISPATCH_FAILED");
        assertThat(f.failureCode.get()).doesNotContain("secret");
    }

    @Test
    void staleLeaseCannotBeMarkedProcessed() {
        Fixture f = new Fixture();
        f.markProcessedResult = false;

        assertThat(f.worker(event -> {}).processNext()).isEqualTo(InboundWorkerDisposition.STALE_LEASE);
        assertThat(f.failureCode.get()).isNull();
    }

    @Test
    void replayAfterSideEffectBeforeAcknowledgementKeepsSameIdempotencyKey() {
        Fixture f = new Fixture();
        f.markProcessedResult = false;
        AtomicReference<String> firstKey = new AtomicReference<>();

        assertThat(f.worker(event -> firstKey.set(event.idempotencyKey())).processNext())
                .isEqualTo(InboundWorkerDisposition.STALE_LEASE);
        assertThat(firstKey.get()).isNotBlank();

        f.item = new InboundWorkItem(
                f.item.key(), f.item.eventType(), f.item.payloadSha256(), f.item.channelType(),
                f.item.branchId(), f.item.correlationId(), f.item.receivedAt(), 2,
                NOW.plusSeconds(30), Optional.of("00000000-0000-0000-0000-000000000100"));
        f.markProcessedResult = true;
        AtomicReference<String> replayKey = new AtomicReference<>();

        assertThat(f.worker(event -> replayKey.set(event.idempotencyKey())).processNext())
                .isEqualTo(InboundWorkerDisposition.PROCESSED);
        assertThat(replayKey.get()).isEqualTo(firstKey.get());
    }

    @Test
    void observerReceivesOutcomeAndSanitizedFailureWithoutChangingProcessing() {
        Fixture f = new Fixture();
        AtomicReference<InboundWorkerDisposition> outcome = new AtomicReference<>();
        AtomicReference<String> errorCode = new AtomicReference<>();
        InboundEventObserver observer = new InboundEventObserver() {
            @Override public void onOutcome(InboundWorkerDisposition disposition) {
                outcome.set(disposition);
                throw new IllegalStateException("metrics backend unavailable");
            }
            @Override public void onFailure(String code, InboundWorkerDisposition disposition) {
                errorCode.set(code);
            }
        };

        InboundWorkerDisposition result = f.worker(
                event -> { throw new IllegalStateException("sensitive provider payload"); }, observer).processNext();

        assertThat(result).isEqualTo(InboundWorkerDisposition.RETRY_SCHEDULED);
        assertThat(outcome.get()).isEqualTo(InboundWorkerDisposition.RETRY_SCHEDULED);
        assertThat(errorCode.get()).isEqualTo("INBOUND_DISPATCH_FAILED");
        assertThat(f.failureCode.get()).isEqualTo("INBOUND_DISPATCH_FAILED");
    }

    @Test
    void unleasedItemIsRejected() {
        Fixture f = new Fixture();
        InboundWorkItem unleased = new InboundWorkItem(
                f.item.key(), f.item.eventType(), f.item.payloadSha256(), f.item.channelType(),
                f.item.branchId(), f.item.correlationId(), f.item.receivedAt(), 0,
                f.item.leaseUntil(), Optional.empty());

        assertThatThrownBy(() -> f.worker(event -> {}).process(unleased))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Fixture {
        InboundWorkItem item = item(DIGEST);
        boolean processed;
        boolean markProcessedResult = true;
        final AtomicReference<String> failureCode = new AtomicReference<>();

        InboundWorkItem item(String digest) {
            return new InboundWorkItem(
                    new InboundEventKey(TenantId.of("00000000-0000-0000-0000-000000000001"),
                            "provider", "account-1", "event-1"),
                    "order.updated", digest, ChannelType.MARKETPLACE, Optional.empty(), "corr-1",
                    NOW.minusSeconds(5), 1, NOW.plusSeconds(30),
                    Optional.of("00000000-0000-0000-0000-000000000099"));
        }

        InboundEventWorker worker(InboundEventDispatcher dispatcher) {
            return worker(dispatcher, InboundEventObserver.NOOP);
        }

        InboundEventWorker worker(InboundEventDispatcher dispatcher, InboundEventObserver observer) {
            InboundEventProcessingStore processing = new InboundEventProcessingStore() {
                @Override public Optional<InboundWorkItem> claimNext(Instant now, Instant leaseUntil, int maxAttempts) {
                    return Optional.of(item);
                }
                @Override public boolean markProcessed(InboundWorkItem claimed, Instant completedAt) {
                    processed = markProcessedResult;
                    return markProcessedResult;
                }
                @Override public boolean recordFailure(InboundWorkItem claimed, String code, Instant retryAt, int maxAttempts) {
                    failureCode.set(code);
                    return true;
                }
                @Override public int deadLetterExhausted(Instant now, int maxAttempts) { return 0; }
                @Override public List<InboundWorkItem> findDeadLetters(int limit) { return List.of(); }
            };
            InboundVerifiedEventMetadataStore metadata = claimed -> claimed.leaseToken().isPresent()
                    ? Optional.of(new InboundVerifiedEventMetadata(REFERENCE, NOW.minusSeconds(4), Optional.empty()))
                    : Optional.empty();
            InboundPayloadStore payloads = new InboundPayloadStore() {
                @Override public InboundPayloadReference store(ChannelContext context, byte[] payload, String expectedSha256, Instant retentionUntil) {
                    return REFERENCE;
                }
                @Override public Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference) {
                    assertThat(context.tenantId().toString()).isEqualTo("00000000-0000-0000-0000-000000000001");
                    assertThat(reference).isEqualTo(REFERENCE);
                    return Optional.of(BODY.clone());
                }
                @Override public boolean delete(ChannelContext context, InboundPayloadReference reference) { return true; }
            };
            return new InboundEventWorker(processing, metadata, payloads, dispatcher,
                    Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30), Duration.ofSeconds(10), 5, observer);
        }
    }
}
