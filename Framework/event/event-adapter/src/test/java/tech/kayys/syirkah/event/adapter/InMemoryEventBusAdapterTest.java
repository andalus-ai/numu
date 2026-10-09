package tech.kayys.syirkah.event.adapter;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.event.adapter.inmemory.InMemoryEventBusAdapter;
import tech.kayys.syirkah.event.domain.BusinessEvent;
import tech.kayys.syirkah.event.domain.EventEnvelope;
import tech.kayys.syirkah.event.domain.identifier.CorrelationId;
import tech.kayys.syirkah.event.domain.valueobject.EventMetadata;
import tech.kayys.syirkah.event.domain.valueobject.EventVersion;
import tech.kayys.syirkah.foundation.kernel.deployment.DeploymentTopology;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventBusAdapterTest {

    record TestBusinessEvent(
            UUID eventId,
            String orderId,
            double amount,
            Instant occurredAt
    ) implements BusinessEvent {
        @Override
        public String eventType() { return "order.created"; }
        @Override
        public EventVersion eventVersion() { return EventVersion.of(1, 0); }
    }

    @Test
    void shouldPublishAndReceiveEventInMemory() {
        InMemoryEventBusAdapter bus = new InMemoryEventBusAdapter();
        AtomicBoolean received = new AtomicBoolean(false);
        List<String> orderIds = new ArrayList<>();

        bus.subscribe("order.created", envelope -> {
            received.set(true);
            TestBusinessEvent evt = (TestBusinessEvent) envelope.payload();
            orderIds.add(evt.orderId());
            return Uni.createFrom().voidItem();
        });

        EventMetadata meta = EventMetadata.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "cashier-1",
                CorrelationId.generate()
        );
        EventEnvelope<TestBusinessEvent> envelope = EventEnvelope.of(
                meta,
                new TestBusinessEvent(UUID.randomUUID(), "ORD-101", 50.0, Instant.now())
        );

        bus.publish(envelope).await().atMost(Duration.ofSeconds(5));

        assertThat(received.get()).isTrue();
        assertThat(orderIds).containsExactly("ORD-101");
    }

    @Test
    void shouldResolveDualModeFactoryCorrectly() {
        assertThat(DualModeEventFactory.resolveMode(DeploymentTopology.STANDALONE))
                .isEqualTo(DualModeEventFactory.EventDeliveryMode.IN_MEMORY);

        assertThat(DualModeEventFactory.resolveMode(DeploymentTopology.MODULAR_MONOLITH))
                .isEqualTo(DualModeEventFactory.EventDeliveryMode.IN_MEMORY);

        assertThat(DualModeEventFactory.resolveMode(DeploymentTopology.MICROSERVICE))
                .isEqualTo(DualModeEventFactory.EventDeliveryMode.DISTRIBUTED_KAFKA);

        assertThat(DualModeEventFactory.resolveMode(DeploymentTopology.SAAS_MULTI_TENANT))
                .isEqualTo(DualModeEventFactory.EventDeliveryMode.DISTRIBUTED_KAFKA);
    }
}
