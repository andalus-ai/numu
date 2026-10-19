package tech.kayys.numou.foundation.application.transaction;

import tech.kayys.numou.foundation.domain.entity.AggregateRoot;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.*;

/**
 * Execution-local context holding touched aggregates, collected domain events,
 * and lifecycle synchronizations for an active {@link UnitOfWork}.
 */
public final class UnitOfWorkContext {

    private final Set<AggregateRoot<?>> touchedAggregates =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<AggregateRoot<?>> eventsCollected =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final List<DomainEvent> domainEvents = new ArrayList<>();
    private final List<UnitOfWorkSynchronization> synchronizations = new ArrayList<>();

    private boolean completed;
    private boolean rolledBack;

    /**
     * Tracks an aggregate root instance touched during this unit of work.
     *
     * @param aggregate the aggregate root
     */
    public void track(AggregateRoot<?> aggregate) {
        requireActive();
        if (aggregate != null) {
            touchedAggregates.add(aggregate);
        }
    }

    /**
     * Extracts pending domain events from the specified aggregate into this unit of work.
     * Idempotent per aggregate instance.
     *
     * @param aggregate the aggregate root
     */
    public void collectEvents(AggregateRoot<?> aggregate) {
        if (aggregate == null) {
            return;
        }
        track(aggregate);

        if (!eventsCollected.add(aggregate)) {
            return;
        }

        var pulled = aggregate.pullDomainEvents();
        if (pulled != null && !pulled.isEmpty()) {
            domainEvents.addAll(pulled);
        }
    }

    /**
     * Collects pending domain events from all touched aggregates.
     */
    public void collectEventsFromTouched() {
        for (var aggregate : touchedAggregates) {
            collectEvents(aggregate);
        }
    }

    /**
     * @return unmodifiable set of all aggregates touched in this unit of work
     */
    public Set<AggregateRoot<?>> touchedAggregates() {
        return Collections.unmodifiableSet(touchedAggregates);
    }

    /**
     * @return unmodifiable snapshot of domain events harvested during this unit of work
     */
    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }

    /**
     * Registers a synchronization callback.
     *
     * @param synchronization the synchronization callback
     */
    public void register(UnitOfWorkSynchronization synchronization) {
        requireActive();
        synchronizations.add(Objects.requireNonNull(synchronization, "synchronization cannot be null"));
    }

    /**
     * @return unmodifiable list of registered synchronizations
     */
    public List<UnitOfWorkSynchronization> synchronizations() {
        return List.copyOf(synchronizations);
    }

    public void markCompleted() {
        requireActive();
        completed = true;
    }

    public void markRolledBack() {
        requireActive();
        rolledBack = true;
    }

    public boolean completed() {
        return completed;
    }

    public boolean rolledBack() {
        return rolledBack;
    }

    public boolean isActive() {
        return !completed && !rolledBack;
    }

    private void requireActive() {
        if (completed || rolledBack) {
            throw new IllegalStateException("UnitOfWork is already completed or rolled back");
        }
    }
}
