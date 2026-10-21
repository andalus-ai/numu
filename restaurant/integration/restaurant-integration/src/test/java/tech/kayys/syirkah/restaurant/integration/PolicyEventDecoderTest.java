package tech.kayys.syirkah.restaurant.integration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.syirkah.restaurant.order.domain.event.CourseFired;
import tech.kayys.syirkah.restaurant.venue.domain.event.TableCleaned;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyEventDecoderTest {

    // Production mapper has JavaTimeModule; this minimal Instant (de)serialisation stands in for it.
    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new SimpleModule()
            .addSerializer(Instant.class, ToStringSerializer.instance)
            .addDeserializer(Instant.class, new StdDeserializer<>(Instant.class) {
                @Override public Instant deserialize(JsonParser p, DeserializationContext c) throws IOException { return Instant.parse(p.getValueAsString()); }
            }));

    private static JsonDomainEvent toWire(DomainEvent e) {
        final var data = MAPPER.valueToTree(e);
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).remove("eventId");
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).remove("occurredAt");
        return new JsonDomainEvent(e.eventId(), e.occurredAt().toString(), e.eventType(), data);
    }

    final PolicyEventDecoder decoder = new PolicyEventDecoder(MAPPER);

    @Test
    void courseFiredSurvivesTheWireIncludingNestedLines() {
        final var line = new CourseFired.Line(UUID.randomUUID(), UUID.randomUUID(), "Rendang", 2, List.of("extra spicy"), "no peanuts",
                List.of("coconut"), 720, UUID.randomUUID());
        final var original = CourseFired.at(Instant.parse("2026-10-10T12:00:00Z"), UUID.randomUUID(), UUID.randomUUID(), 1, "T5", List.of(line));
        final var back = (CourseFired) decoder.decode(toWire(original)).orElseThrow();
        assertThat(back).isEqualTo(original);
    }

    @Test
    void tableCleanedDecodes() {
        final var original = TableCleaned.at(Instant.parse("2026-10-10T12:00:00Z"), UUID.randomUUID(), UUID.randomUUID(), 4);
        assertThat(decoder.decode(toWire(original))).contains(original);
    }

    @Test
    void eventsWithoutPoliciesAreIgnoredNotErrors() {
        final var wire = new JsonDomainEvent(UUID.randomUUID(), "2026-10-10T12:00:00Z", "restaurant.reservation.confirmed", MAPPER.createObjectNode());
        assertThat(decoder.decode(wire)).isEmpty();
    }

    @Test
    void malformedPayloadIsAPermanentError() {
        final var wire = new JsonDomainEvent(UUID.randomUUID(), "2026-10-10T12:00:00Z", "restaurant.table.cleaned", MAPPER.createObjectNode().put("maxCovers", "not-a-number"));
        assertThatThrownBy(() -> decoder.decode(wire)).isInstanceOf(IllegalArgumentException.class);
    }
}
