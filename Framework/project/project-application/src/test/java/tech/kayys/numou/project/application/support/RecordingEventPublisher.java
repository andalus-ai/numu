package tech.kayys.syirkah.project.application.support;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

/** Records everything the handlers publish so tests can assert on it. */
public final class RecordingEventPublisher implements EventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public Uni<Void> publish(List<DomainEvent> events) {
        published.addAll(events);
        return Uni.createFrom().voidItem();
    }

    public List<DomainEvent> published() {
        return List.copyOf(published);
    }

    /** Forgets everything recorded so far, so a test can assert on one step. */
    public void reset() {
        published.clear();
    }

    public List<Class<?>> publishedTypes() {
        return published.stream()
                .<Class<?>>map(event -> event.getClass())
                .toList();
    }
}
