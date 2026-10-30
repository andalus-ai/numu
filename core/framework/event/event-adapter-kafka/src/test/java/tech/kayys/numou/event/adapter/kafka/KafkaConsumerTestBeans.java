package tech.kayys.numou.event.adapter.kafka;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.valueobject.EventVersion;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
class KafkaConsumerTestBeans {

    @Produces
    @ApplicationScoped
    EventTypeRegistry eventTypeRegistry() {
        return (eventType, version) -> "pos.sale.completed".equals(eventType)
                && EventVersion.V1.equals(version)
                ? Optional.of(KafkaConsumerConnectorTest.TestSaleCompleted.class)
                : Optional.empty();
    }
}

@ApplicationScoped
class KafkaConsumerProbe {

    private final BlockingQueue<EventEnvelope<? extends BusinessEvent>> received =
            new ArrayBlockingQueue<>(16);

    @Inject
    KafkaEventSubscriberAdapter subscriber;

    @PostConstruct
    void register() {
        subscriber.subscribe("pos.sale.completed", envelope -> {
            received.offer(envelope);
            return Uni.createFrom().voidItem();
        });
    }

    EventEnvelope<?> await(Duration timeout) throws InterruptedException {
        EventEnvelope<? extends BusinessEvent> envelope =
                received.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (envelope == null) {
            throw new AssertionError("Timed out waiting for Kafka event consumption");
        }
        return envelope;
    }
}
