package tech.kayys.numou.mobility.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import tech.kayys.numou.aggregate.memory.DispatchingEventSink;
import tech.kayys.numou.event.spi.port.EventSerializerPort;
import tech.kayys.numou.mobility.integration.MobilityEventDecoder;
import tech.kayys.numou.mobility.integration.MobilityPolicies;
import tech.kayys.numou.mobility.ride.application.RideSettlementService;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.numou.reliability.application.inbox.InboxProcessor;

import java.util.List;

/**
 * Event-driven settlement trigger behind the inbox: a redelivered "ride completed" is recognised and skipped, and one whose settlement
 * failed is left unhandled so the broker redelivers it. The sweeper is the independent backstop. NOT COMPILED in the authoring sandbox.
 */
@ApplicationScoped
public class MobilityEventConsumer {

    @Inject ObjectMapper mapper;
    @Inject EventSerializerPort serializer;
    @Inject InboxProcessor inbox;
    @Inject RideSettlementService settlement;

    private DispatchingEventSink bus;
    private MobilityEventDecoder decoder;

    @PostConstruct
    void init() {
        bus = new DispatchingEventSink(false, true);
        MobilityPolicies.register(bus, settlement);
        decoder = new MobilityEventDecoder(mapper);
    }

    @Incoming("mobility-events")
    public Uni<Void> consume(String json) {
        final var envelope = serializer.deserialize(json, null);
        final var wire = (JsonDomainEvent) envelope.payload();
        if (envelope.metadata().tenantId() == null) return Uni.createFrom().voidItem();
        return inbox.process(wire.eventId().toString(), wire.eventType(), () ->
                decoder.decode(wire).map(e -> bus.publish(envelope.metadata().tenantId(), List.of(e))).orElseGet(() -> Uni.createFrom().voidItem())).replaceWithVoid();
    }
}
