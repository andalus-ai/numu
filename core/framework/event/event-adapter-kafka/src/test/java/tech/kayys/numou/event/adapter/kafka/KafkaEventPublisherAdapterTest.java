package tech.kayys.numou.event.adapter.kafka;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaEventPublisherAdapterTest {

    private record TestEvent(String reference) implements BusinessEvent {
        @Override
        public UUID eventId() {
            return UUID.nameUUIDFromBytes(reference.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public Instant occurredAt() {
            return Instant.EPOCH;
        }

        @Override
        public String eventType() {
            return "pos.sale.completed";
        }

        @Override
        public EventVersion eventVersion() {
            return EventVersion.V1;
        }
    }

    @Test
    void publishesSerializedEnvelopeAndWaitsForSenderAcknowledgement() {
        List<String> sent = new ArrayList<>();
        KafkaEventPublisherAdapter adapter = new KafkaEventPublisherAdapter(
                message -> {
                    sent.add(message.getPayload());
                    return Uni.createFrom().voidItem();
                },
                serializer(),
                "numou.events.");

        adapter.publish(envelope("SALE-1")).await().atMost(Duration.ofSeconds(1));

        assertThat(sent).containsExactly("pos.sale.completed:SALE-1");
    }

    @Test
    void propagatesBrokerNegativeAcknowledgement() {
        RuntimeException brokerFailure = new RuntimeException("broker unavailable");
        KafkaEventPublisherAdapter adapter = new KafkaEventPublisherAdapter(
                message -> Uni.createFrom().failure(brokerFailure),
                serializer(),
                "numou.events.");

        assertThatThrownBy(() -> adapter.publish(envelope("SALE-2"))
                .await().atMost(Duration.ofSeconds(1)))
                .isSameAs(brokerFailure);
    }

    @Test
    void publishesBatchSequentiallyAndStopsAfterFirstFailure() {
        List<String> sent = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        KafkaEventPublisherAdapter adapter = new KafkaEventPublisherAdapter(
                message -> {
                    sent.add(message.getPayload());
                    if (calls.incrementAndGet() == 2) {
                        return Uni.createFrom().failure(new RuntimeException("nack"));
                    }
                    return Uni.createFrom().voidItem();
                },
                serializer(),
                "numou.events.");

        assertThatThrownBy(() -> adapter.publishAll(List.of(
                envelope("SALE-1"), envelope("SALE-2"), envelope("SALE-3")))
                .await().atMost(Duration.ofSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("nack");

        assertThat(sent).containsExactly(
                "pos.sale.completed:SALE-1",
                "pos.sale.completed:SALE-2");
    }

    @Test
    void rejectsInvalidEventTopicSuffix() {
        BusinessEvent invalid = new BusinessEvent() {
            @Override
            public UUID eventId() {
                return UUID.nameUUIDFromBytes("invalid-event".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public Instant occurredAt() {
                return Instant.EPOCH;
            }

            @Override
            public String eventType() {
                return "pos/sale";
            }

            @Override
            public EventVersion eventVersion() {
                return EventVersion.V1;
            }
        };
        EventEnvelope<BusinessEvent> envelope = EventEnvelope.of(metadata(), invalid);
        KafkaEventPublisherAdapter adapter = new KafkaEventPublisherAdapter(
                message -> Uni.createFrom().voidItem(),
                serializer(),
                "numou.events.");

        assertThatThrownBy(() -> adapter.publish(envelope).await().atMost(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid Kafka topic suffix");
    }

    private static EventEnvelope<TestEvent> envelope(String reference) {
        return EventEnvelope.of(metadata(), new TestEvent(reference));
    }

    private static EventMetadata metadata() {
        return EventMetadata.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "pos-test",
                CorrelationId.generate());
    }

    private static EventSerializerPort serializer() {
        return new EventSerializerPort() {
            @Override
            public String serialize(EventEnvelope<? extends BusinessEvent> envelope) {
                TestEvent event = (TestEvent) envelope.payload();
                return envelope.eventType() + ":" + event.reference();
            }

            @Override
            public EventEnvelope<? extends BusinessEvent> deserialize(String payload, String eventType) {
                throw new UnsupportedOperationException("Not used by publisher tests");
            }
        };
    }
}
