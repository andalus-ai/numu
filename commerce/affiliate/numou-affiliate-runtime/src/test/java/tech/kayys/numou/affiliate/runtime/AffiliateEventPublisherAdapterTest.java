package tech.kayys.numou.affiliate.runtime;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.ClaimedEvent;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.spi.port.EventPublisherPort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateEventPublisherAdapterTest {
    @Test
    void publishesVersionedEnvelopeAndPreservesOutboxIdentity() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-01T10:15:30Z");
        ClaimedEvent claimed = new ClaimedEvent("tenant-opaque", eventId, "PartnerPayout",
                aggregateId, "affiliate.payout.submitted", "stable-event-key", "{\"amount\":\"125.00\"}",
                2, "worker-1", occurredAt.plusSeconds(30), occurredAt);
        EventMetadata metadata = new EventMetadata(EventId.of(eventId),
                CorrelationId.of("corr-123"), null, UUID.randomUUID(), UUID.randomUUID(),
                "system:affiliate-outbox", occurredAt.toString(), "trace-123");
        AtomicReference<EventEnvelope<? extends BusinessEvent>> published = new AtomicReference<>();
        EventPublisherPort port = new EventPublisherPort() {
            @Override public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
                published.set(envelope);
                return Uni.createFrom().voidItem();
            }
            @Override public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
                return Uni.createFrom().failure(new UnsupportedOperationException("not used"));
            }
        };
        AffiliateEventPublisherAdapter adapter = new AffiliateEventPublisherAdapter(port, ignored -> metadata);

        adapter.publish(claimed).toCompletableFuture().join();

        EventEnvelope<? extends BusinessEvent> envelope = published.get();
        assertNotNull(envelope);
        assertEquals(eventId, envelope.metadata().eventId().value());
        assertEquals("corr-123", envelope.metadata().correlationId().value());
        assertEquals("affiliate.payout.submitted", envelope.eventType());
        assertEquals("stable-event-key", ((AffiliateOutboxBusinessEvent) envelope.payload()).eventKey());
        assertEquals(occurredAt, ((AffiliateOutboxBusinessEvent) envelope.payload()).occurredAt());
        assertEquals(2, ((AffiliateOutboxBusinessEvent) envelope.payload()).attempts());
    }

    @Test
    void rejectsResolverThatChangesOutboxEventIdentity() {
        Instant occurredAt = Instant.parse("2026-11-01T10:15:30Z");
        ClaimedEvent claimed = new ClaimedEvent("tenant-opaque", UUID.randomUUID(), "PartnerPayout",
                UUID.randomUUID(), "affiliate.payout.submitted", "event-key", "{}", 1,
                "worker-1", occurredAt.plusSeconds(30), occurredAt);
        EventMetadata wrongMetadata = new EventMetadata(EventId.generate(),
                CorrelationId.of("corr"), null, UUID.randomUUID(), UUID.randomUUID(),
                "system", occurredAt.toString(), null);
        EventPublisherPort unused = new EventPublisherPort() {
            @Override public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
                return Uni.createFrom().voidItem();
            }
            @Override public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
                return Uni.createFrom().voidItem();
            }
        };
        AffiliateEventPublisherAdapter adapter = new AffiliateEventPublisherAdapter(unused, ignored -> wrongMetadata);

        CompletionStage<Void> publication = adapter.publish(claimed);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> publication.toCompletableFuture().join());
    }
}
