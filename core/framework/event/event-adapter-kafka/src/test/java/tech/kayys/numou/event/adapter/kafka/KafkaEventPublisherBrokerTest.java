package tech.kayys.numou.event.adapter.kafka;

import io.smallrye.mutiny.Uni;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KafkaEventPublisherBrokerTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @Test
    void publishesResolvedTopicKeyPayloadAndVersionedHeadersToBroker() {
        String eventId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String topic = "numou.events.pos.sale.completed";
        String payload = "{\"wireFormatVersion\":1,\"eventType\":\"pos.sale.completed\"}";

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties());
             Consumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(List.of(topic));

            KafkaEventPublisherAdapter adapter = new KafkaEventPublisherAdapter(message -> {
                var metadata = message.getMetadata(
                        io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata.class)
                        .orElseThrow();
                String key = (String) metadata.getKey();
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        metadata.getTopic(), key, message.getPayload());
                metadata.getHeaders().forEach(header -> record.headers().add(header));
                try {
                    producer.send(record).get();
                    return Uni.createFrom().voidItem();
                } catch (Exception failure) {
                    return Uni.createFrom().failure(failure);
                }
            }, serializer(payload), "numou.events.");

            EventEnvelope<TestEvent> envelope = EventEnvelope.of(
                    new EventMetadata(EventId.of(UUID.fromString(eventId)), CorrelationId.generate(), null,
                            UUID.fromString(tenantId), UUID.randomUUID(), "pos-test",
                            java.time.Instant.now().toString(), null), new TestEvent());
            adapter.publish(envelope).await().atMost(Duration.ofSeconds(20));

            org.apache.kafka.clients.consumer.ConsumerRecord<String, String> received = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline && received == null) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                if (!records.isEmpty()) received = records.iterator().next();
            }

            assertThat(received).isNotNull();
            assertThat(received.topic()).isEqualTo(topic);
            assertThat(received.key()).isEqualTo(tenantId);
            assertThat(received.value()).isEqualTo(payload);
            assertThat(header(received, "numou-event-id")).isEqualTo(eventId);
            assertThat(header(received, "numou-event-type")).isEqualTo("pos.sale.completed");
            assertThat(header(received, "numou-event-version")).isEqualTo("1.0");
        }
    }

    private static Properties producerProperties() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return p;
    }

    private static Properties consumerProperties() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "numou-event-publisher-broker-it");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return p;
    }

    private static String header(
            org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static EventSerializerPort serializer(String payload) {
        return new EventSerializerPort() {
            @Override
            public String serialize(EventEnvelope<? extends BusinessEvent> envelope) {
                return payload;
            }

            @Override
            public EventEnvelope<? extends BusinessEvent> deserialize(String wirePayload, String eventType) {
                throw new UnsupportedOperationException("Not used by this test");
            }
        };
    }

    private static final class TestEvent implements BusinessEvent {
        @Override public UUID eventId() { return UUID.randomUUID(); }
        @Override public Instant occurredAt() { return Instant.now(); }
        @Override public String eventType() { return "pos.sale.completed"; }
        @Override public EventVersion eventVersion() { return EventVersion.V1; }
    }
}
