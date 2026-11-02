package tech.kayys.numou.event.application.replay;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-authorized application boundary for replaying a broker DLQ record.
 *
 * <p>The original envelope is deserialized through the registered event
 * contract and republished unchanged. The source topic/partition/offset is
 * only a recovery locator; consumer inbox idempotency remains keyed by the
 * original event ID.
 */
public final class DeadLetterReplayService {
    private final DeadLetterReplayOperations operations;
    private final DeadLetterReplayAuthorizer authorizer;
    private final EventSerializerPort serializer;
    private final EventPublisherPort publisher;
    private final Clock clock;
    private final Duration leaseDuration;

    public DeadLetterReplayService(
            DeadLetterReplayOperations operations,
            DeadLetterReplayAuthorizer authorizer,
            EventSerializerPort serializer,
            EventPublisherPort publisher,
            Clock clock,
            Duration leaseDuration) {
        this.operations = Objects.requireNonNull(operations, "operations must not be null");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    public Uni<DeadLetterReplayResult> replay(
            UUID tenantId, DeadLetterSourceKey source, String authenticatedActorId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        String actor = bounded(authenticatedActorId, "authenticatedActorId", 200);
        String safeReason = bounded(reason, "reason", 500);
        if (!authorizer.mayReplay(tenantId, source, actor)) {
            return Uni.createFrom().failure(new SecurityException("Not authorized to replay this tenant DLQ record"));
        }

        var claimed = operations.claim(tenantId, source, actor, safeReason, clock.instant(), leaseDuration);
        if (claimed.isEmpty()) {
            return Uni.createFrom().item(new DeadLetterReplayResult(
                    DeadLetterReplayResult.Status.ALREADY_CLAIMED, null));
        }

        DeadLetterReplayClaim claim = claimed.orElseThrow();
        EventEnvelope<? extends BusinessEvent> envelope;
        try {
            DeadLetterReplayRecord record = claim.record();
            if (!tenantId.equals(record.tenantId()) || !source.equals(record.source())) {
                throw new SecurityException("DLQ claim does not match requested tenant and source");
            }
            envelope = serializer.deserialize(record.payload(), record.eventType());
            if (envelope == null || envelope.payload() == null
                    || !record.eventType().equals(envelope.eventType())
                    || !tenantId.equals(envelope.metadata().tenantId())) {
                throw new IllegalArgumentException("DLQ envelope tenant or event contract does not match its source");
            }
        } catch (RuntimeException invalidRecord) {
            operations.markFailed(tenantId, source, claim.claimToken(),
                    safeFailure(invalidRecord), clock.instant());
            return Uni.createFrom().failure(invalidRecord);
        }

        UUID originalEventId = envelope.metadata().eventId().value();
        return publisher.publish(envelope)
                .chain(() -> {
                    if (!operations.markPublished(tenantId, source, claim.claimToken(), clock.instant())) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "DLQ replay claim fencing token was lost after publish"));
                    }
                    return Uni.createFrom().item(new DeadLetterReplayResult(
                            DeadLetterReplayResult.Status.PUBLISHED, originalEventId));
                })
                .onFailure().call(failure -> Uni.createFrom().item(() ->
                        operations.markFailed(tenantId, source, claim.claimToken(),
                                safeFailure(failure), clock.instant())).replaceWithVoid());
    }

    private static String bounded(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        }
        return normalized;
    }

    private static String safeFailure(Throwable failure) {
        String message = failure.getMessage();
        String safe = failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }
}
