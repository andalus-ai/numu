package tech.kayys.syirkah.identity.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.util.List;

/**
 * Publishes domain events to Kafka via SmallRye Reactive Messaging.
 *
 * NOTE: this is a direct publish, not yet backed by a transactional
 * outbox - so it does not (on its own) give exactly-once/atomic
 * "commit the DB write and publish the event together" guarantees.
 * A real outbox table + relay is the next hardening step once this
 * bounded context has a use case that needs that guarantee - not
 * something to speculatively build before it's needed.
 */
@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {

    @Inject
    @Channel("identity-events")
    MutinyEmitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Uni<Void> publish(List<DomainEvent> events) {
        if (events.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return Uni.combine().all()
                .unis(events.stream().map(this::publishOne).toList())
                .discardItems();
    }

    private Uni<Void> publishOne(DomainEvent event) {
        try {
            var payload = objectMapper.writeValueAsString(event);
            return emitter.send(payload);
        } catch (Exception exception) {
            return Uni.createFrom().failure(exception);
        }
    }

}
