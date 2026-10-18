package tech.kayys.numou.survey.application.port.out;

import tech.kayys.numou.survey.domain.event.DomainEvent;

import java.util.List;

/** Outbound port for publishing domain events after aggregate persistence. */
public interface EventPublisher {
    void publish(List<DomainEvent> events);
}
