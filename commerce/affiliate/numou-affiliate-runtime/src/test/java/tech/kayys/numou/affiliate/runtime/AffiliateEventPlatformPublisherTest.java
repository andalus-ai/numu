package tech.kayys.numou.affiliate.runtime;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.ClaimedEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.spi.port.EventPublisherPort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateEventPlatformPublisherTest {
    @Test
    void preservesOutboxIdentityTenantAndPayloadAtTheEventPlatformBoundary() {
        UUID eventId = UUID.randomUUID();
        UUID tenantUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-31T10:00:00Z");
        ClaimedEvent claimed = new ClaimedEvent("tenant-external-key", eventId, "AffiliatePartner",
                UUID.randomUUID(), "affiliate.partner.created", "partner-created-1",
                "{\"status\":\"ACTIVE\"}", 1, "worker-a", occurredAt.plusSeconds(30), occurredAt);
        AtomicReference<EventEnvelope<? extends BusinessEvent>> captured = new AtomicReference<>();
        EventPublisherPort port = new EventPublisherPort() {
            @Override public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
                captured.set(envelope);
                return Uni.createFrom().voidItem();
            }
            @Override public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
                return Uni.createFrom().voidItem();
            }
        };
        AffiliateEventPlatformPublisher adapter = new AffiliateEventPlatformPublisher(port, event ->
                new EventMetadata(EventId.of(event.id()), CorrelationId.of("corr-9"), null,
                        tenantUuid, null, "operator-9", occurredAt.toString(), null));

        adapter.publish(claimed).toCompletableFuture().join();

        EventEnvelope<? extends BusinessEvent> envelope = captured.get();
        assertNotNull(envelope);
        assertEquals(eventId, envelope.metadata().eventId().value());
        assertEquals(tenantUuid, envelope.metadata().tenantId());
        assertEquals("affiliate.partner.created", envelope.eventType());
        assertEquals(EventVersion.V1, envelope.envelopeVersion());
        assertEquals(occurredAt, envelope.payload().occurredAt());
        assertEquals("{\"status\":\"ACTIVE\"}", ((AffiliateOutboxBusinessEvent) envelope.payload()).payload());
    }

    @Test
    void refusesTenantIdentityGuessingOrEventIdentityReplacement() {
        UUID eventId = UUID.randomUUID();
        ClaimedEvent claimed = new ClaimedEvent("not-a-uuid-tenant-key", eventId, "AffiliatePartner",
                UUID.randomUUID(), "affiliate.partner.created", "key", "{}", 1, "worker",
                Instant.now().plusSeconds(30), Instant.now());
        EventPublisherPort port = new EventPublisherPort() {
            @Override public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
                return Uni.createFrom().voidItem();
            }
            @Override public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
                return Uni.createFrom().voidItem();
            }
        };
        AffiliateEventPlatformPublisher noTenant = new AffiliateEventPlatformPublisher(port, event ->
                new EventMetadata(EventId.of(event.id()), CorrelationId.of("corr"), null,
                        null, null, "system", Instant.now().toString(), null));
        assertThrows(IllegalArgumentException.class, () -> noTenant.publish(claimed));

        AffiliateEventPlatformPublisher wrongEvent = new AffiliateEventPlatformPublisher(port, event ->
                new EventMetadata(EventId.generate(), CorrelationId.of("corr"), null,
                        UUID.randomUUID(), null, "system", Instant.now().toString(), null));
        assertThrows(IllegalArgumentException.class, () -> wrongEvent.publish(claimed));
    }
}