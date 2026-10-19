package tech.kayys.numou.commerce.offering.application.support;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public void reset() {
        published.clear();
    }
}
