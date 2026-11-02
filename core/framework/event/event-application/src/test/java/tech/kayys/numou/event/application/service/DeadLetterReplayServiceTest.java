package tech.kayys.numou.event.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.application.command.ReplayDeadLetterCommand;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.*;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DeadLetterReplayServiceTest {
    private final UUID tenant = UUID.randomUUID();
    private final EventEnvelope<TestEvent> original = EventEnvelope.of(
            new EventMetadata(EventId.generate(), CorrelationId.generate(), null, tenant,
                    UUID.randomUUID(), "source-actor", "2026-10-31T00:00:00Z", "trace-1"),
            new TestEvent("sale-1"));

    @Test void republishesOriginalEnvelopeAndCompletesAuditClaim() {
        FakeStore store = new FakeStore();
        FakePublisher publisher = new FakePublisher();
        var service = service(store, publisher, (t,a,r) -> Uni.createFrom().voidItem());
        var result = service.replay(command(tenant)).await().indefinitely();
        assertEquals(DeadLetterReplayService.ReplayStatus.PUBLISHED, result.status());
        assertSame(original, publisher.published);
        assertEquals("operator-1", store.request.actorId());
        assertEquals("validated operational recovery", store.request.reason());
        assertTrue(store.completed.get());
    }

    @Test void rejectsCrossTenantPayloadBeforeClaimOrPublish() {
        FakeStore store = new FakeStore();
        FakePublisher publisher = new FakePublisher();
        var service = service(store, publisher, (t,a,r) -> Uni.createFrom().voidItem());
        assertThrows(SecurityException.class, () -> service.replay(command(UUID.randomUUID())).await().indefinitely());
        assertNull(store.request);
        assertNull(publisher.published);
    }

    @Test void authorizationFailureStopsBeforeStoreAndPublisher() {
        FakeStore store = new FakeStore();
        FakePublisher publisher = new FakePublisher();
        var service = service(store, publisher,
                (t,a,r) -> Uni.createFrom().failure(new SecurityException("denied")));
        assertThrows(SecurityException.class, () -> service.replay(command(tenant)).await().indefinitely());
        assertNull(store.request);
        assertNull(publisher.published);
    }

    @Test void inProgressClaimPreventsConcurrentDuplicatePublish() {
        FakeStore store = new FakeStore();
        store.status = DeadLetterReplayStorePort.ClaimStatus.IN_PROGRESS;
        FakePublisher publisher = new FakePublisher();
        var result = service(store, publisher, (t,a,r) -> Uni.createFrom().voidItem())
                .replay(command(tenant)).await().indefinitely();
        assertEquals(DeadLetterReplayService.ReplayStatus.IN_PROGRESS, result.status());
        assertNull(publisher.published);
    }

    @Test void failedPublishIsAuditedAndNotMarkedComplete() {
        FakeStore store = new FakeStore();
        FakePublisher publisher = new FakePublisher();
        publisher.fail = true;
        assertThrows(IllegalStateException.class, () -> service(store, publisher,
                (t,a,r) -> Uni.createFrom().voidItem()).replay(command(tenant)).await().indefinitely());
        assertTrue(store.failed.get());
        assertFalse(store.completed.get());
    }

    private DeadLetterReplayService service(FakeStore store, FakePublisher publisher,
            DeadLetterReplayAuthorizationPort authorization) {
        EventSerializerPort serializer = new EventSerializerPort() {
            public String serialize(EventEnvelope<? extends BusinessEvent> e) { return "payload"; }
            public EventEnvelope<? extends BusinessEvent> deserialize(String p, String t) { return original; }
        };
        return new DeadLetterReplayService(authorization, store, serializer, publisher);
    }

    private ReplayDeadLetterCommand command(UUID tenantId) {
        return new ReplayDeadLetterCommand("dlq-record-1", tenantId, "operator-1",
                "validated operational recovery", "pos.sale.completed", "payload");
    }

    private static final class FakePublisher implements EventPublisherPort {
        EventEnvelope<? extends BusinessEvent> published;
        boolean fail;
        public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> e) {
            published = e;
            return fail ? Uni.createFrom().failure(new IllegalStateException("broker unavailable"))
                    : Uni.createFrom().voidItem();
        }
        public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> e) {
            return Uni.createFrom().voidItem();
        }
    }

    private static final class FakeStore implements DeadLetterReplayStorePort {
        ReplayRequest request;
        ClaimStatus status = ClaimStatus.ACQUIRED;
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        public Uni<ReplayClaim> beginReplay(ReplayRequest request) {
            this.request = request;
            return Uni.createFrom().item(new ReplayClaim(request.deadLetterRecordId(),
                    request.tenantId(), "fenced-token", 1, status));
        }
        public Uni<Void> completeReplay(ReplayClaim claim) {
            completed.set(true); return Uni.createFrom().voidItem();
        }
        public Uni<Void> failReplay(ReplayClaim claim, String code) {
            failed.set(true); return Uni.createFrom().voidItem();
        }
    }

    private record TestEvent(String id) implements BusinessEvent {
        public UUID eventId() { return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)); }
        public Instant occurredAt() { return Instant.parse("2026-10-31T00:00:00Z"); }
        public String eventType() { return "pos.sale.completed"; }
        public EventVersion eventVersion() { return EventVersion.V1; }
    }
}
