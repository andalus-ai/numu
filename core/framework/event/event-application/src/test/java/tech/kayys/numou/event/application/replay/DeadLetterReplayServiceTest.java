package tech.kayys.numou.event.application.replay;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadLetterReplayServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final DeadLetterSourceKey SOURCE = new DeadLetterSourceKey("cluster-a", "numou.events.pos.sale", 2, 41);
    private static final String ACTOR = "operator-17";
    private static final String REASON = "provider outage resolved";

    @Test
    void rejectsUnauthorizedReplayBeforeClaiming() {
        FakeOperations operations = new FakeOperations(record(TENANT));
        DeadLetterReplayService service = service(operations, (tenant, source, actor) -> false, envelope(TENANT));

        assertThatThrownBy(() -> service.replay(TENANT, SOURCE, ACTOR, REASON).await().indefinitely())
                .isInstanceOf(SecurityException.class);

        assertThat(operations.claims).hasValue(0);
    }

    @Test
    void rejectsCrossTenantEnvelopeAndRecordsFailure() {
        FakeOperations operations = new FakeOperations(record(TENANT));
        AtomicInteger publishes = new AtomicInteger();
        EventEnvelope<TestEvent> wrongTenant = envelope(UUID.randomUUID());
        DeadLetterReplayService service = service(operations, (tenant, source, actor) -> true, wrongTenant,
                ignored -> {
                    publishes.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });

        assertThatThrownBy(() -> service.replay(TENANT, SOURCE, ACTOR, REASON).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(publishes).hasValue(0);
        assertThat(operations.failed).hasValue(1);
        assertThat(operations.published).hasValue(0);
    }

    @Test
    void republishesOriginalEnvelopeAndCompletesOnlyItsClaim() {
        EventEnvelope<TestEvent> original = envelope(TENANT);
        FakeOperations operations = new FakeOperations(record(TENANT));
        AtomicInteger publishes = new AtomicInteger();
        DeadLetterReplayService service = service(operations, (tenant, source, actor) -> true, original,
                envelope -> {
                    publishes.incrementAndGet();
                    assertThat(envelope.metadata().eventId()).isEqualTo(original.metadata().eventId());
                    assertThat(envelope.metadata().tenantId()).isEqualTo(TENANT);
                    return Uni.createFrom().voidItem();
                });

        DeadLetterReplayResult result = service.replay(TENANT, SOURCE, ACTOR, REASON).await().indefinitely();

        assertThat(result.status()).isEqualTo(DeadLetterReplayResult.Status.PUBLISHED);
        assertThat(result.eventId()).isEqualTo(original.metadata().eventId().value());
        assertThat(publishes).hasValue(1);
        assertThat(operations.published).hasValue(1);
        assertThat(operations.failed).hasValue(0);
        assertThat(operations.claimedActor).isEqualTo(ACTOR);
        assertThat(operations.claimedReason).isEqualTo(REASON);
    }

    private static DeadLetterReplayService service(
            FakeOperations operations,
            DeadLetterReplayAuthorizer authorizer,
            EventEnvelope<TestEvent> envelope) {
        return service(operations, authorizer, envelope, ignored -> Uni.createFrom().voidItem());
    }

    private static DeadLetterReplayService service(
            FakeOperations operations,
            DeadLetterReplayAuthorizer authorizer,
            EventEnvelope<TestEvent> envelope,
            java.util.function.Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> send) {
        EventSerializerPort serializer = new EventSerializerPort() {
            @Override public String serialize(EventEnvelope<? extends BusinessEvent> value) { return "wire"; }
            @Override public EventEnvelope<? extends BusinessEvent> deserialize(String payload, String eventType) {
                assertThat(payload).isEqualTo("wire");
                assertThat(eventType).isEqualTo("pos.sale.completed");
                return envelope;
            }
        };
        EventPublisherPort publisher = new EventPublisherPort() {
            @Override public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> value) { return send.apply(value); }
            @Override public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> values) {
                return Uni.createFrom().voidItem();
            }
        };
        return new DeadLetterReplayService(operations, authorizer, serializer, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(2));
    }

    private static DeadLetterReplayRecord record(UUID tenant) {
        return new DeadLetterReplayRecord(tenant, SOURCE, "pos.sale.completed", "wire",
                "handler retries exhausted", NOW.minusSeconds(30));
    }

    private static EventEnvelope<TestEvent> envelope(UUID tenant) {
        EventMetadata metadata = new EventMetadata(EventId.generate(), CorrelationId.generate(), null,
                tenant, UUID.randomUUID(), ACTOR, NOW.toString(), "trace-123");
        return EventEnvelope.of(metadata, new TestEvent());
    }

    private static final class TestEvent implements BusinessEvent {
        @Override public String eventType() { return "pos.sale.completed"; }
        @Override public EventVersion eventVersion() { return EventVersion.V1; }
    }

    private static final class FakeOperations implements DeadLetterReplayOperations {
        private final DeadLetterReplayRecord record;
        private final AtomicInteger claims = new AtomicInteger();
        private final AtomicInteger published = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private String claimedActor;
        private String claimedReason;

        private FakeOperations(DeadLetterReplayRecord record) { this.record = record; }

        @Override public Optional<DeadLetterReplayClaim> claim(
                UUID tenant, DeadLetterSourceKey source, String actor, String reason,
                Instant requestedAt, Duration leaseDuration) {
            claims.incrementAndGet();
            claimedActor = actor;
            claimedReason = reason;
            if (!record.tenantId().equals(tenant) || !record.source().equals(source)) return Optional.empty();
            return Optional.of(new DeadLetterReplayClaim(UUID.randomUUID(), record));
        }

        @Override public boolean markPublished(UUID tenant, DeadLetterSourceKey source, UUID token, Instant at) {
            published.incrementAndGet();
            return true;
        }

        @Override public boolean markFailed(UUID tenant, DeadLetterSourceKey source, UUID token, String failure, Instant at) {
            failed.incrementAndGet();
            return true;
        }
    }
}
