package tech.kayys.numou.foundation.application.transaction;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultUnitOfWorkTest {

    record TestId(String value) implements DomainId<String> {
        @Override
        public String toString() {
            return value;
        }
    }

    record SampleEvent(UUID eventId, String info, Instant occurredAt) implements DomainEvent {
        SampleEvent(String info) {
            this(UUID.randomUUID(), info, Instant.now());
        }

        @Override
        public String eventType() {
            return "SampleEvent";
        }
    }

    static class SampleAggregate extends AbstractAggregateRoot<TestId> {
        SampleAggregate(String id) {
            super(new TestId(id));
        }

        void doSomething(String info) {
            raise(new SampleEvent(info));
        }
    }

    @Test
    void shouldTrackAggregateAndHarvestEventsOnCommit() {
        List<DomainEvent> published = new ArrayList<>();
        EventPublisher publisher = events -> {
            published.addAll(events);
            return Uni.createFrom().voidItem();
        };

        var uow = new DefaultUnitOfWork(publisher);
        var aggregate = new SampleAggregate("agg-1");

        String result = uow.execute(() -> {
            uow.track(aggregate);
            aggregate.doSomething("event-1");
            return Uni.createFrom().item("success");
        }).await().atMost(Duration.ofSeconds(2));

        assertEquals("success", result);
        assertEquals(1, published.size());
        assertEquals("event-1", ((SampleEvent) published.get(0)).info());
        assertTrue(aggregate.pullDomainEvents().isEmpty(), "Events should have been pulled during UoW execution");
    }

    @Test
    void shouldExecuteLifecycleSynchronizationsInOrder() {
        List<String> lifecycleSteps = new ArrayList<>();

        var uow = new DefaultUnitOfWork();
        var sync = new UnitOfWorkSynchronization() {
            @Override
            public Uni<Void> beforeCommit(UnitOfWorkContext context) {
                lifecycleSteps.add("beforeCommit");
                return Uni.createFrom().voidItem();
            }

            @Override
            public Uni<Void> afterCommit(UnitOfWorkContext context) {
                lifecycleSteps.add("afterCommit");
                return Uni.createFrom().voidItem();
            }

            @Override
            public Uni<Void> afterRollback(UnitOfWorkContext context, Throwable cause) {
                lifecycleSteps.add("afterRollback");
                return Uni.createFrom().voidItem();
            }
        };

        uow.execute(() -> {
            uow.register(sync);
            lifecycleSteps.add("work");
            return Uni.createFrom().item("done");
        }).await().atMost(Duration.ofSeconds(2));

        assertEquals(List.of("work", "beforeCommit", "afterCommit"), lifecycleSteps);
    }

    @Test
    void shouldExecuteRollbackOnFailureAndNotPublishEvents() {
        List<String> lifecycleSteps = new ArrayList<>();
        List<DomainEvent> published = new ArrayList<>();
        EventPublisher publisher = events -> {
            published.addAll(events);
            return Uni.createFrom().voidItem();
        };

        var uow = new DefaultUnitOfWork(publisher);
        var aggregate = new SampleAggregate("agg-2");

        var sync = new UnitOfWorkSynchronization() {
            @Override
            public Uni<Void> afterRollback(UnitOfWorkContext context, Throwable cause) {
                lifecycleSteps.add("afterRollback: " + cause.getMessage());
                return Uni.createFrom().voidItem();
            }

            @Override
            public Uni<Void> afterCommit(UnitOfWorkContext context) {
                lifecycleSteps.add("afterCommit");
                return Uni.createFrom().voidItem();
            }
        };

        assertThrows(RuntimeException.class, () ->
            uow.execute(() -> {
                uow.register(sync);
                uow.track(aggregate);
                aggregate.doSomething("fail-event");
                return Uni.createFrom().<String>failure(new RuntimeException("database failure"));
            }).await().atMost(Duration.ofSeconds(2))
        );

        assertEquals(List.of("afterRollback: database failure"), lifecycleSteps);
        assertTrue(published.isEmpty(), "No events should be published on rollback");
    }

    @Test
    void shouldJoinExistingContextOnNestedExecute() {
        var uow = new DefaultUnitOfWork();
        List<String> traces = new ArrayList<>();

        uow.execute(() -> {
            traces.add("outer-begin");
            var outerCtx = uow.context().orElseThrow();

            return uow.execute(() -> {
                traces.add("inner-execute");
                var innerCtx = uow.context().orElseThrow();
                assertSame(outerCtx, innerCtx, "Nested execute must share the same UnitOfWorkContext");
                return Uni.createFrom().item("inner");
            }).chain(innerRes -> {
                traces.add("outer-end");
                return Uni.createFrom().item(innerRes + "-outer");
            });
        }).await().atMost(Duration.ofSeconds(2));

        assertEquals(List.of("outer-begin", "inner-execute", "outer-end"), traces);
        assertTrue(uow.context().isEmpty(), "Context should be cleared after outer execution completes");
    }
}
