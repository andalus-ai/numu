package tech.kayys.numou.event.adapter;

import tech.kayys.numou.event.adapter.distributed.DistributedKafkaEventPublisherAdapter;
import tech.kayys.numou.event.adapter.inmemory.InMemoryEventBusAdapter;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSubscriberPort;
import tech.kayys.numou.foundation.kernel.deployment.DeploymentTopology;

/**
 * Factory for creating the appropriate event publisher and subscriber
 * based on deployment topology (In-Memory for Standalone/Monolith vs Kafka for Microservices/SaaS).
 */
public final class DualModeEventFactory {

    public enum EventDeliveryMode {
        IN_MEMORY,
        DISTRIBUTED_KAFKA
    }

    public static EventDeliveryMode resolveMode(DeploymentTopology topology) {
        if (topology == null || topology.isStandalone() || topology == DeploymentTopology.MODULAR_MONOLITH) {
            return EventDeliveryMode.IN_MEMORY;
        }
        return EventDeliveryMode.DISTRIBUTED_KAFKA;
    }

    public static InMemoryEventBusAdapter createInMemoryBus() {
        return new InMemoryEventBusAdapter();
    }

    public static DistributedKafkaEventPublisherAdapter createKafkaBus(String bootstrapServers, String topicPrefix) {
        return new DistributedKafkaEventPublisherAdapter(bootstrapServers, topicPrefix);
    }

    public static EventPublisherPort createPublisher(EventDeliveryMode mode) {
        return switch (mode) {
            case IN_MEMORY -> new InMemoryEventBusAdapter();
            case DISTRIBUTED_KAFKA -> new DistributedKafkaEventPublisherAdapter();
        };
    }

    public static EventSubscriberPort createSubscriber(EventDeliveryMode mode) {
        return switch (mode) {
            case IN_MEMORY -> new InMemoryEventBusAdapter();
            case DISTRIBUTED_KAFKA -> new DistributedKafkaEventPublisherAdapter();
        };
    }
}
