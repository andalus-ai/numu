package tech.kayys.numou.survey.adapter.memory;

import tech.kayys.numou.survey.application.port.out.EventPublisher;
import tech.kayys.numou.survey.domain.event.DomainEvent;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Process-local event sink for the in-memory Survey runtime.
 *
 * This adapter is intended for development and tests; it does not provide
 * durable delivery or cross-process event distribution.
 */
public final class InMemoryEventPublisher implements EventPublisher {
    private final Queue<DomainEvent> published = new ConcurrentLinkedQueue<>();

    @Override
    public void publish(List<DomainEvent> events) {
        published.addAll(events);
    }

    public List<DomainEvent> publishedEvents() {
        return List.copyOf(published);
    }
}
