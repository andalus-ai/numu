package tech.kayys.syirkah.commerce.subscription.application.support;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test double capturing published domain events.
 */
public final class RecordingEventPublisher implements EventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public Uni<Void> publish(List<DomainEvent> events) {
        published.addAll(events);
        return Uni.createFrom().voidItem();
    }

    public List<DomainEvent> published() {
        return Collections.unmodifiableList(published);
    }
}
