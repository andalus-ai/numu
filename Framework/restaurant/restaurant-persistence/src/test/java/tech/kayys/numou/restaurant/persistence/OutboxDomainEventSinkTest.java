package tech.kayys.syirkah.restaurant.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.reliability.adapter.json.*;
import tech.kayys.syirkah.reliability.outbox.OutboxEntry;
import tech.kayys.syirkah.reliability.spi.port.OutboxStorePort;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxDomainEventSinkTest {

    record ReservationCancelled(UUID eventId, Instant occurredAt, UUID reservationId, String outcome, Instant startsAt) implements DomainEvent {
        public String eventType() { return "restaurant.reservation.cancelled"; }
    }

    static final class MemStore implements OutboxStorePort {
        final List<OutboxEntry> rows = new ArrayList<>();
        public Uni<Void> append(List<OutboxEntry> e) { rows.addAll(e); return Uni.createFrom().voidItem(); }
        public Uni<List<OutboxEntry>> nextDue(int l) { return Uni.createFrom().item(List.of()); }
        public Uni<Void> update(OutboxEntry e) { return Uni.createFrom().voidItem(); }
        public Uni<OutboxEntry> findById(UUID id) { return Uni.createFrom().nullItem(); }
        public Uni<Long> countPending() { return Uni.createFrom().item(0L); }
    }

    // The production mapper has JavaTimeModule; here a minimal Instant serializer stands in for it.
    final ObjectMapper mapper = new ObjectMapper().registerModule(new SimpleModule().addSerializer(Instant.class, ToStringSerializer.instance));
    final MemStore store = new MemStore();
    final UUID tenant = UUID.randomUUID();
    final OutboxDomainEventSink sink = new OutboxDomainEventSink(store, mapper, () -> "host-7", () -> "corr-42");

    @Test
    void eventsBecomeOutboxEntriesWithTenantActorAndCorrelation() {
        final var ev = new ReservationCancelled(UUID.randomUUID(), Instant.parse("2026-10-07T12:00:00Z"), UUID.randomUUID(), "NONE", Instant.parse("2026-10-10T12:00:00Z"));
        sink.publish(tenant, List.of(ev)).await().indefinitely();
        assertThat(store.rows).hasSize(1);
        final var meta = store.rows.get(0).envelope().metadata();
        assertThat(meta.tenantId()).isEqualTo(tenant);
        assertThat(meta.actorId()).isEqualTo("host-7");
        assertThat(meta.correlationId().value()).isEqualTo("corr-42");
        assertThat(meta.eventId().value()).isEqualTo(ev.eventId());      // aggregate-generated id is preserved for dedupe
        assertThat(store.rows.get(0).envelope().eventType()).isEqualTo("restaurant.reservation.cancelled");
    }

    @Test
    void roundTripsThroughTheWireFormatUsingTheFallbackType() {
        final var registry = new EventTypeRegistry().registerFallback(JsonDomainEvent.class);
        final var serializer = new JsonEventSerializer(mapper, registry);
        final var ev = new ReservationCancelled(UUID.randomUUID(), Instant.parse("2026-10-07T12:00:00Z"), UUID.randomUUID(), "LATE_FEE", Instant.parse("2026-10-10T12:00:00Z"));
        sink.publish(tenant, List.of(ev)).await().indefinitely();

        final var json = serializer.serialize(store.rows.get(0).envelope());
        final var back = serializer.deserialize(json, "restaurant.reservation.cancelled");
        assertThat(back.metadata().tenantId()).isEqualTo(tenant);
        final var payload = (JsonDomainEvent) back.payload();
        assertThat(payload.data().get("outcome").asText()).isEqualTo("LATE_FEE");
        assertThat(payload.data().get("reservationId").asText()).isEqualTo(ev.reservationId().toString());
        assertThat(payload.data().has("eventId")).isFalse();      // identity is in the envelope, not repeated
        assertThat(payload.occurredAt()).isEqualTo(ev.occurredAt());
    }

    @Test
    void emptyBatchTouchesNothing() {
        sink.publish(tenant, List.of()).await().indefinitely();
        assertThat(store.rows).isEmpty();
    }

    @Test
    void unknownTypeWithoutFallbackStaysAPermanentError() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EventTypeRegistry().classFor("x.y"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
