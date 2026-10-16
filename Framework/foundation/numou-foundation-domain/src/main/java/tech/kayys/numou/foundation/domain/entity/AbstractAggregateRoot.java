package tech.kayys.numou.foundation.domain.entity;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base implementation for aggregate roots.
 *
 * @param <ID> aggregate identifier
 */
public abstract class AbstractAggregateRoot<ID extends DomainId<?>>
        implements AggregateRoot<ID>, Serializable {

    private static final long serialVersionUID = 1L;

    private final List<DomainEvent> domainEvents = new ArrayList<>();
    protected ID id;
    protected Instant createdAt;
    protected Instant updatedAt;
    protected int version;

    protected AbstractAggregateRoot(ID id) {
        this.id = id;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    protected AbstractAggregateRoot() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    @Override
    public ID id() {
        return id;
    }

    public ID getId() {
        return id();
    }

    protected void setId(ID id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void incrementVersion() {
        this.version++;
    }

    /**
     * Registers a domain event raised by this aggregate.
     */
    protected final void raise(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Domain event cannot be null"
            );
        }

        domainEvents.add(event);
    }

    protected void registerEvent(DomainEvent event) {
        raise(event);
    }

    /**
     * Returns all pending events and clears the internal collection.
     */
    @Override
    public final List<DomainEvent> pullDomainEvents() {
        if (domainEvents.isEmpty()) {
            return List.of();
        }

        final var events = List.copyOf(domainEvents);
        domainEvents.clear();

        return events;
    }

    public void clearEvents() {
        pullDomainEvents();
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
