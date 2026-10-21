package tech.kayys.numou.mobility.integration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.mobility.ride.domain.event.RideCancelled;
import tech.kayys.numou.mobility.ride.domain.event.RideCompleted;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobilityEventDecoderTest {

    // The production mapper has JavaTimeModule; this minimal Instant handling stands in for it.
    static final ObjectMapper MAPPER = tech.kayys.numou.reliability.adapter.json.JsonMapperConfig.apply(new ObjectMapper()).registerModule(new SimpleModule()
            .addSerializer(Instant.class, ToStringSerializer.instance)
            .addDeserializer(Instant.class, new StdDeserializer<>(Instant.class) {
                @Override public Instant deserialize(JsonParser p, DeserializationContext c) throws IOException { return Instant.parse(p.getValueAsString()); }
            }));

    private static JsonDomainEvent toWire(DomainEvent e) {
        final ObjectNode data = MAPPER.valueToTree(e);
        data.remove("eventId"); data.remove("occurredAt");
        return new JsonDomainEvent(e.eventId(), e.occurredAt().toString(), e.eventType(), data);
    }

    final MobilityEventDecoder decoder = new MobilityEventDecoder(MAPPER);

    @Test
    void rideCompletedSurvivesTheWire() {
        final var original = RideCompleted.at(Instant.parse("2026-10-10T10:30:00Z"), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CAR", "WALLET",
                "IDR", new BigDecimal("28000"), new BigDecimal("5600"), new BigDecimal("1000"), new BigDecimal("23400"), 5123.4, 912, "UPFRONT");
        assertThat(decoder.decode(toWire(original))).contains(original);
    }

    @Test
    void rideCancelledWithNoDriverDecodesNullDriver() {
        final var original = RideCancelled.at(Instant.parse("2026-10-10T10:30:00Z"), UUID.randomUUID(), UUID.randomUUID(), null, "RIDER", BigDecimal.ZERO, "IDR", false);
        assertThat(decoder.decode(toWire(original))).contains(original);
    }

    @Test
    void unrelatedEventsAreIgnoredAndMalformedOnesArePermanentErrors() {
        assertThat(decoder.decode(new JsonDomainEvent(UUID.randomUUID(), "2026-10-10T10:00:00Z", "mobility.driver.online", MAPPER.createObjectNode()))).isEmpty();
        assertThatThrownBy(() -> decoder.decode(new JsonDomainEvent(UUID.randomUUID(), "2026-10-10T10:00:00Z", "mobility.ride.completed",
                MAPPER.createObjectNode().put("distanceMeters", "not-a-number")))).isInstanceOf(IllegalArgumentException.class);
    }
}
