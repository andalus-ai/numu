package tech.kayys.numou.foundation.domain.entity;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.List;

/**
 * Root entity of a consistency boundary.
 *
 * An aggregate is not simply an entity with child entities - it is the
 * transactional consistency boundary of the domain model. Everything
 * inside it is loaded, changed and persisted together.
 *
 * @param <ID> aggregate identifier
 */
public interface AggregateRoot<ID extends DomainId<?>>
        extends Entity<ID> {

    /**
     * Pulls pending domain events from the aggregate.
     *
     * The returned events are removed from the aggregate.
     */
    List<DomainEvent> pullDomainEvents();

}
