package tech.kayys.syirkah.survey.adapter.memory;

import tech.kayys.syirkah.survey.application.port.out.EventPublisher;
import tech.kayys.syirkah.survey.domain.event.DomainEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps published events in memory so tests/demos can assert on them. Swap for an outbox or broker adapter. */
public final class CollectingEventPublisher implements EventPublisher {
    private final List<DomainEvent> published = new CopyOnWriteArrayList<>();

    @Override public void publish(List<DomainEvent> events) { published.addAll(events); }

    public List<DomainEvent> published() { return List.copyOf(published); }
}
