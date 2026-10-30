package tech.kayys.numou.event.adapter;

import tech.kayys.numou.event.adapter.distributed.DistributedKafkaEventPublisherAdapter;
import tech.kayys.numou.event.adapter.inmemory.InMemoryEventBusAdapter;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSubscriberPort;
import tech.kayys.numou.foundation.kernel.deployment.DeploymentTopology;

import java.util.Objects;

/**
 * Selects event ports from deployment topology.
 *
 * <p>Distributed transport is an explicit composition decision. This factory
 * never creates a pretend Kafka adapter or silently falls back to in-memory
 * delivery for a distributed deployment.
 */
public final class DualModeEventFactory {

    private DualModeEventFactory() {
    }

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

    /**
     * @deprecated The former implementation did not connect to Kafka. Supply
     * a real transport adapter through the composition root instead.
     */
    @Deprecated(forRemoval = true)
    public static DistributedKafkaEventPublisherAdapter createKafkaBus(
            String bootstrapServers, String topicPrefix) {
        throw distributedTransportRequired();
    }

    /**
     * Creates an event publisher. Distributed mode requires a real,
     * externally configured adapter; it is never constructed implicitly.
     */
    public static EventPublisherPort createPublisher(
            EventDeliveryMode mode, EventPublisherPort distributedPublisher) {
        Objects.requireNonNull(mode, "mode must not be null");
        return switch (mode) {
            case IN_MEMORY -> new InMemoryEventBusAdapter();
            case DISTRIBUTED_KAFKA -> Objects.requireNonNull(
                    distributedPublisher,
                    "A configured distributed EventPublisherPort is required")
                    ;
        };
    }

    /**
     * Creates an event subscriber. Distributed mode requires a real,
     * externally configured consumer adapter.
     */
    public static EventSubscriberPort createSubscriber(
            EventDeliveryMode mode, EventSubscriberPort distributedSubscriber) {
        Objects.requireNonNull(mode, "mode must not be null");
        return switch (mode) {
            case IN_MEMORY -> new InMemoryEventBusAdapter();
            case DISTRIBUTED_KAFKA -> Objects.requireNonNull(
                    distributedSubscriber,
                    "A configured distributed EventSubscriberPort is required")
                    ;
        };
    }

    /**
     * @deprecated Use the overload that receives a configured distributed
     * adapter. This method cannot safely construct one.
     */
    @Deprecated(forRemoval = true)
    public static EventPublisherPort createPublisher(EventDeliveryMode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        if (mode == EventDeliveryMode.DISTRIBUTED_KAFKA) {
            throw distributedTransportRequired();
        }
        return new InMemoryEventBusAdapter();
    }

    /**
     * @deprecated Use the overload that receives a configured distributed
     * adapter. This method cannot safely construct one.
     */
    @Deprecated(forRemoval = true)
    public static EventSubscriberPort createSubscriber(EventDeliveryMode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        if (mode == EventDeliveryMode.DISTRIBUTED_KAFKA) {
            throw distributedTransportRequired();
        }
        return new InMemoryEventBusAdapter();
    }

    private static IllegalStateException distributedTransportRequired() {
        return new IllegalStateException(
                "Distributed event transport is not implemented; configure a real "
                        + "EventPublisherPort/EventSubscriberPort before selecting DISTRIBUTED_KAFKA");
    }
}
