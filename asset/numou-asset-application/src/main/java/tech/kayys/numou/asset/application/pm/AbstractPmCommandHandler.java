package tech.kayys.numou.asset.application.pm;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.List;
import java.util.Objects;

/** Shared orchestration for PM (ASSET-22) write use cases: UnitOfWork + outbox publish. */
public abstract class AbstractPmCommandHandler {
    protected final EventPublisher eventPublisher;
    protected final UnitOfWork unitOfWork;
    protected final DomainClock clock;

    protected AbstractPmCommandHandler(EventPublisher eventPublisher, UnitOfWork unitOfWork, DomainClock clock) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    protected <T extends AbstractAggregateRoot<?>> Uni<T> saveAndPublish(
            java.util.concurrent.CompletionStage<Void> saveStage, T aggregate) {
        return unitOfWork.execute(() -> Uni.createFrom().completionStage(() -> saveStage)
                .flatMap(ignored -> publishPendingEvents(aggregate).replaceWith(aggregate)));
    }

    private Uni<Void> publishPendingEvents(AbstractAggregateRoot<?> aggregate) {
        List<DomainEvent> events = aggregate.pullDomainEvents();
        if (events.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return eventPublisher.publish(events);
    }
}
