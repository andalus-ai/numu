package tech.kayys.numou.omnichannel.core;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Claims and dispatches verified inbound events using the durable inbox lifecycle.
 *
 * <p>The lease token fences metadata reads and completion/failure updates. A lease
 * can still expire while a downstream call is in flight, so dispatchers must make
 * business effects idempotent using the event's scoped identity or target use-case
 * idempotency support.</p>
 */
public final class InboundEventWorker {
    private final InboundEventProcessingStore processingStore;
    private final InboundVerifiedEventMetadataStore metadataStore;
    private final InboundPayloadStore payloadStore;
    private final InboundEventDispatcher dispatcher;
    private final Clock clock;
    private final Duration leaseDuration;
    private final InboundRetryPolicy retryPolicy;
    private final int maxAttempts;

    public InboundEventWorker(
            InboundEventProcessingStore processingStore,
            InboundVerifiedEventMetadataStore metadataStore,
            InboundPayloadStore payloadStore,
            InboundEventDispatcher dispatcher,
            Clock clock,
            Duration leaseDuration,
            Duration retryDelay,
            int maxAttempts) {
        this(processingStore, metadataStore, payloadStore, dispatcher, clock, leaseDuration,
                fixedRetryPolicy(retryDelay), maxAttempts);
    }

    public InboundEventWorker(
            InboundEventProcessingStore processingStore,
            InboundVerifiedEventMetadataStore metadataStore,
            InboundPayloadStore payloadStore,
            InboundEventDispatcher dispatcher,
            Clock clock,
            Duration leaseDuration,
            InboundRetryPolicy retryPolicy,
            int maxAttempts) {
        this.processingStore = Objects.requireNonNull(processingStore, "processingStore must not be null");
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore must not be null");
        this.payloadStore = Objects.requireNonNull(payloadStore, "payloadStore must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
    }

    /** Claims at most one due item and handles it. Suitable for a scheduler or worker loop. */
    public InboundWorkerDisposition processNext() {
        Instant now = clock.instant();
        Optional<InboundWorkItem> claimed = processingStore.claimNext(now, now.plus(leaseDuration), maxAttempts);
        if (claimed.isEmpty()) return InboundWorkerDisposition.NO_WORK;
        return process(claimed.get());
    }

    /** Processes a claimed item; callers must pass the exact leased item returned by the store. */
    public InboundWorkerDisposition process(InboundWorkItem item) {
        Objects.requireNonNull(item, "item must not be null");
        if (!item.isLeased()) throw new IllegalArgumentException("a leased work item is required");

        try {
            InboundVerifiedEventMetadata metadata = metadataStore.findForLease(item)
                    .orElseThrow(() -> new WorkerFailure("LEASE_OR_VERIFIED_METADATA_UNAVAILABLE"));
            ChannelContext context = new ChannelContext(
                    item.key().tenantId(),
                    item.branchId(),
                    item.channelType(),
                    item.key().providerAccountId(),
                    item.correlationId());
            byte[] payload = payloadStore.load(context, metadata.payloadReference())
                    .orElseThrow(() -> new WorkerFailure("PAYLOAD_UNAVAILABLE"));
            if (!constantTimeEquals(item.payloadSha256(), sha256(payload))) {
                throw new WorkerFailure("PAYLOAD_DIGEST_MISMATCH");
            }

            InboundEventEnvelope envelope = new InboundEventEnvelope(
                    context,
                    item.key().externalEventId(),
                    item.eventType(),
                    item.payloadSha256(),
                    item.receivedAt(),
                    metadata.providerOccurredAt());
            dispatcher.dispatch(new VerifiedInboundEvent(
                    item.key().providerId(), envelope, metadata.payloadReference(), metadata.verifiedAt()));

            return processingStore.markProcessed(item, clock.instant())
                    ? InboundWorkerDisposition.PROCESSED
                    : InboundWorkerDisposition.STALE_LEASE;
        } catch (WorkerFailure failure) {
            return fail(item, failure.code());
        } catch (RuntimeException failure) {
            // Never persist provider payloads or arbitrary exception messages as error codes.
            return fail(item, "INBOUND_DISPATCH_FAILED");
        }
    }

    private InboundWorkerDisposition fail(InboundWorkItem item, String code) {
        Instant now = clock.instant();
        Duration delay = positive(Objects.requireNonNull(retryPolicy.delayFor(item),
                "retryPolicy must return a delay"), "retry delay");
        boolean updated = processingStore.recordFailure(item, code, now.plus(delay), maxAttempts);
        if (!updated) return InboundWorkerDisposition.STALE_LEASE;
        return item.attemptCount() >= maxAttempts
                ? InboundWorkerDisposition.DEAD_LETTERED
                : InboundWorkerDisposition.RETRY_SCHEDULED;
    }

    private static InboundRetryPolicy fixedRetryPolicy(Duration retryDelay) {
        Duration delay = positive(retryDelay, "retryDelay");
        return item -> delay;
    }

    private static Duration positive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                right.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static final class WorkerFailure extends RuntimeException {
        private final String code;
        private WorkerFailure(String code) { super(code); this.code = code; }
        private String code() { return code; }
    }
}
