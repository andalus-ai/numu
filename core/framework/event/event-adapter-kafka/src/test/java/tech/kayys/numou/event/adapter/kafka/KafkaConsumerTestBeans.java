package tech.kayys.numou.event.adapter.kafka;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
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


@ApplicationScoped
class KafkaIndependentGroupProbe {

    private final BlockingQueue<String> received = new ArrayBlockingQueue<>(16);

    @Incoming("numou-events-observer")
    void observe(String payload) {
        received.offer(payload);
    }

    String awaitContaining(String marker, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            String payload = received.poll(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)),
                    TimeUnit.MILLISECONDS);
            if (payload == null) {
                break;
            }
            if (payload.contains(marker)) {
                return payload;
            }
        }
        throw new AssertionError("Timed out waiting for independent consumer group event containing " + marker);
    }
}
