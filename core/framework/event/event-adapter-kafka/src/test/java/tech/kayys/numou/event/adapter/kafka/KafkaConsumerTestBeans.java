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


@ApplicationScoped
class KafkaRetryPolicyProbe {

    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger> attempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    KafkaEventSubscriberAdapter subscriber;

    @PostConstruct
    void register() {
        subscriber.subscribe("pos.sale.completed", envelope -> {
            String saleId = ((KafkaConsumerConnectorTest.TestSaleCompleted) envelope.payload()).saleId;
            if (!saleId.startsWith("retry-success-") && !saleId.startsWith("retry-terminal-")) {
                return Uni.createFrom().voidItem();
            }
            java.util.concurrent.atomic.AtomicInteger count =
                    attempts.computeIfAbsent(saleId, ignored -> new java.util.concurrent.atomic.AtomicInteger());
            boolean terminal = saleId.startsWith("retry-terminal-");
            return Uni.createFrom().deferred(() -> {
                int attempt = count.incrementAndGet();
                if (terminal || attempt < 3) {
                    return Uni.createFrom().failure(new IllegalStateException("simulated transient handler failure"));
                }
                return Uni.createFrom().voidItem();
            }).onFailure().retry().atMost(2);
        });
    }

    int attempts(String saleId) {
        java.util.concurrent.atomic.AtomicInteger count = attempts.get(saleId);
        return count == null ? 0 : count.get();
    }

    void awaitAttempts(String saleId, int expected, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (attempts(saleId) >= expected) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + expected + " handler attempts for " + saleId
                + "; observed " + attempts(saleId));
    }
}
