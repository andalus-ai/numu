package tech.kayys.numou.foundation.domain.entity;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.identifier.DomainId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AbstractAggregateRootTest {

    record TestId(UUID value)
            implements DomainId<UUID> {
    }

    record SomethingHappened(
            UUID eventId,
            Instant occurredAt
    ) implements DomainEvent {

        @Override
        public String eventType() {
            return "test.something-happened";
        }
    }

    static final class TestAggregate
            extends AbstractAggregateRoot<TestId> {

        private final TestId id;

        TestAggregate(TestId id) {
            this.id = id;
        }

        void doSomething() {
            raise(
                    new SomethingHappened(
                            UUID.randomUUID(),
                            Instant.now()
                    )
            );
        }

        @Override
        public TestId id() {
            return id;
        }
    }

    @Test
    void pullsAndClearsDomainEvents() {
        var aggregate = new TestAggregate(new TestId(UUID.randomUUID()));

        aggregate.doSomething();

        var events = aggregate.pullDomainEvents();

        assertEquals(1, events.size());
        assertTrue(aggregate.pullDomainEvents().isEmpty());
    }

}
