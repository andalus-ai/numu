package tech.kayys.syirkah.restaurant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import tech.kayys.syirkah.event.spi.port.EventSerializerPort;
import tech.kayys.syirkah.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.syirkah.reliability.application.inbox.InboxProcessor;
import tech.kayys.syirkah.restaurant.integration.DispatchingEventSink;
import tech.kayys.syirkah.restaurant.integration.PolicyEventDecoder;
import tech.kayys.syirkah.restaurant.integration.RestaurantPolicies;
import tech.kayys.syirkah.restaurant.kitchen.application.KitchenService;
import tech.kayys.syirkah.restaurant.order.application.OrderService;
import tech.kayys.syirkah.reservation.application.WaitlistService;

import java.util.List;
import java.util.UUID;

/**
 * Runs the cross-context policies as an outbox consumer: kitchen on "course fired", order on "item ready", and so on.
 *
 * <p>Delivery is at-least-once, so every message passes through the {@link InboxProcessor}: a redelivered event is
 * recognised and skipped, and an event whose policy failed is left unhandled so the broker redelivers it. A message
 * that can never succeed (bad payload) throws {@link IllegalArgumentException}; configure the channel's
 * {@code failure-strategy=dead-letter-queue} so it parks instead of blocking the partition.
 * NOT COMPILED in the authoring sandbox.
 */
@ApplicationScoped
public class RestaurantEventConsumer {

    private static final Logger LOG = Logger.getLogger(RestaurantEventConsumer.class);

    @Inject
    ObjectMapper mapper;
    @Inject
    EventSerializerPort serializer;
    @Inject
    InboxProcessor inbox;
    @Inject
    KitchenService kitchen;
    @Inject
    OrderService orders;
    @Inject
    WaitlistService waitlist;

    private DispatchingEventSink bus;
    private PolicyEventDecoder decoder;

    @PostConstruct
    void init() {
        bus = new DispatchingEventSink(false, true); // dispatch-only (no event memory); failures propagate for redelivery
        RestaurantPolicies.register(bus, kitchen, orders, waitlist);
        decoder = new PolicyEventDecoder(mapper);
    }

    @Incoming("restaurant-events")
    public Uni<Void> consume(String json) {
        final var envelope = serializer.deserialize(json, null);
        final var wire = (JsonDomainEvent) envelope.payload();
        final UUID tenant = envelope.metadata().tenantId();
        if (tenant == null) {
            LOG.warnf("Dropping tenantless event type=%s", wire.eventType()); // restaurant events are always tenant-scoped
            return Uni.createFrom().voidItem();
        }
        return inbox.process(wire.eventId().toString(), wire.eventType(), () ->
                        decoder.decode(wire).map(e -> bus.publish(tenant, List.of(e))).orElseGet(() -> Uni.createFrom().voidItem()))
                .replaceWithVoid();
    }
}
