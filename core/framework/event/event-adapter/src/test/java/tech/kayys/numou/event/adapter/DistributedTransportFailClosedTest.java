package tech.kayys.numou.event.adapter;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.adapter.distributed.DistributedKafkaEventPublisherAdapter;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSubscriberPort;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributedTransportFailClosedTest {

    private static final class TestEvent implements BusinessEvent {
        @Override
        public String eventType() {
            return "test.event";
        }

        @Override
        public EventVersion eventVersion() {
            return EventVersion.V1;
        }
    }

    @Test
    void publisherMustNotAcknowledgeAnEventWithoutABroker() {
        DistributedKafkaEventPublisherAdapter adapter =
                new DistributedKafkaEventPublisherAdapter("broker:9092", "numou.events.");

        EventEnvelope<TestEvent> envelope = EventEnvelope.of(
                null,
                new TestEvent()
        );

        assertThatThrownBy(() -> adapter.publish(envelope).await().atMost(Duration.ofSeconds(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void subscriberMustNotPretendToConsumeFromKafka() {
        DistributedKafkaEventPublisherAdapter adapter =
                new DistributedKafkaEventPublisherAdapter("broker:9092", "numou.events.");

        assertThatThrownBy(() -> adapter.subscribe("test.event", ignored -> Uni.createFrom().voidItem()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void topologyFactoryRequiresExplicitDistributedPublisherAndSubscriber() {
        assertThatThrownBy(() -> DualModeEventFactory.createPublisher(
                DualModeEventFactory.EventDeliveryMode.DISTRIBUTED_KAFKA, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> DualModeEventFactory.createSubscriber(
                DualModeEventFactory.EventDeliveryMode.DISTRIBUTED_KAFKA, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void legacyFactoryCannotSilentlyConstructDistributedTransport() {
        assertThatThrownBy(() -> DualModeEventFactory.createPublisher(
                DualModeEventFactory.EventDeliveryMode.DISTRIBUTED_KAFKA))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> DualModeEventFactory.createSubscriber(
                DualModeEventFactory.EventDeliveryMode.DISTRIBUTED_KAFKA))
                .isInstanceOf(IllegalStateException.class);
    }
}
