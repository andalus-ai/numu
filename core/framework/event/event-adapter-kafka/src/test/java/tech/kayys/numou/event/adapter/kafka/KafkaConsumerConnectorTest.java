package tech.kayys.numou.event.adapter.kafka;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KafkaConsumerTestResource.class)
class KafkaConsumerConnectorTest {

    private static final String EVENT_TYPE = "pos.sale.completed";
    private static final String SOURCE_TOPIC = "numou.events.pos.sale.completed";
    private static final String DEAD_LETTER_TOPIC = "numou.events.dead-letter";

    @Inject
    EventSerializerPort serializer;

    @Inject
    KafkaConsumerProbe probe;

    @Inject
    KafkaIndependentGroupProbe independentGroupProbe;

    @Inject
    KafkaRetryPolicyProbe retryPolicyProbe;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void connectorConsumesAndDispatchesARealBrokerRecord() throws Exception {
        String saleId = UUID.randomUUID().toString();
        EventMetadata expectedMetadata = new EventMetadata(
                EventId.generate(), CorrelationId.generate(), null,
                UUID.randomUUID(), UUID.randomUUID(), "connector-test",
                Instant.now().toString(), "trace-" + UUID.randomUUID());
        EventEnvelope<TestSaleCompleted> envelope = EventEnvelope.of(
                expectedMetadata, new TestSaleCompleted(saleId));

        send(SOURCE_TOPIC, serializer.serialize(envelope), EVENT_TYPE);

        EventEnvelope<?> received = probe.await(Duration.ofSeconds(20));
        assertThat(received.eventType()).isEqualTo(EVENT_TYPE);
        assertThat(received.metadata()).isEqualTo(expectedMetadata);
        assertThat(received.metadata().tenantId()).isEqualTo(expectedMetadata.tenantId());
        assertThat(received.metadata().participantId()).isEqualTo(expectedMetadata.participantId());
        assertThat(received.metadata().actorId()).isEqualTo(expectedMetadata.actorId());
        assertThat(received.metadata().correlationId()).isEqualTo(expectedMetadata.correlationId());
        assertThat(received.metadata().traceId()).isEqualTo(expectedMetadata.traceId());
        assertThat(((TestSaleCompleted) received.payload()).saleId).isEqualTo(saleId);

        String independentlyConsumed = independentGroupProbe.awaitContaining(saleId, Duration.ofSeconds(20));
        assertThat(independentlyConsumed).contains(saleId);
    }

    @Test
    void connectorRoutesMalformedRecordToConfiguredDeadLetterTopic() throws Exception {
        send(SOURCE_TOPIC, "{malformed-json", EVENT_TYPE);

        ConsumerRecord<String, String> deadLetter = pollDeadLetterContaining(bootstrapServers, "{malformed-json", Duration.ofSeconds(25));
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.value()).isEqualTo("{malformed-json");
        assertThat(headerValue(deadLetter, "dead-letter-topic")).isEqualTo(SOURCE_TOPIC);
        assertThat(headerValue(deadLetter, "dead-letter-partition")).isNotBlank();
        assertThat(headerValue(deadLetter, "dead-letter-offset")).isNotBlank();
        assertThat(headerValue(deadLetter, "dead-letter-reason")).isNotBlank();
        assertThat(headerValue(deadLetter, "dead-letter-cause")).isNotBlank();
        assertThat(headerValue(deadLetter, "numou-event-type")).isEqualTo(EVENT_TYPE);
    }


    @Test
    void transientHandlerFailureRetriesWithinBoundAndThenAcknowledges() throws Exception {
        String saleId = "retry-success-" + UUID.randomUUID();
        send(SOURCE_TOPIC, serializer.serialize(retryEnvelope(saleId)), EVENT_TYPE);

        retryPolicyProbe.awaitAttempts(saleId, 3, Duration.ofSeconds(20));
        assertThat(pollDeadLetterContaining(bootstrapServers, saleId, Duration.ofSeconds(2))).isNull();
    }

    @Test
    void exhaustedHandlerRetriesAreDeadLetteredWithOriginalEventIdentity() throws Exception {
        String saleId = "retry-terminal-" + UUID.randomUUID();
        String payload = serializer.serialize(retryEnvelope(saleId));
        send(SOURCE_TOPIC, payload, EVENT_TYPE);

        retryPolicyProbe.awaitAttempts(saleId, 3, Duration.ofSeconds(20));
        ConsumerRecord<String, String> deadLetter =
                pollDeadLetterContaining(bootstrapServers, saleId, Duration.ofSeconds(25));
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.value()).isEqualTo(payload);
        assertThat(headerValue(deadLetter, "dead-letter-topic")).isEqualTo(SOURCE_TOPIC);
        assertThat(headerValue(deadLetter, "numou-event-type")).isEqualTo(EVENT_TYPE);
    }

    private EventEnvelope<TestSaleCompleted> retryEnvelope(String saleId) {
        EventMetadata metadata = new EventMetadata(
                EventId.generate(), CorrelationId.generate(), null,
                UUID.randomUUID(), UUID.randomUUID(), "retry-acceptance-test",
                Instant.now().toString(), "trace-" + UUID.randomUUID());
        return EventEnvelope.of(metadata, new TestSaleCompleted(saleId));
    }

    @Test
    void uncommittedRecordIsRedeliveredAfterRestartAndCommittedOffsetIsRespected() throws Exception {
        String topic = "numou.events.recovery." + UUID.randomUUID().toString().replace("-", "");
        String groupId = "numou-event-recovery-" + UUID.randomUUID();
        String payload = "restart-recovery-" + UUID.randomUUID();
        send(topic, payload, EVENT_TYPE);

        ConsumerRecord<String, String> firstDelivery;
        try (Consumer<String, String> consumer = new KafkaConsumer<>(
                recoveryConsumerProperties(bootstrapServers, groupId))) {
            consumer.subscribe(List.of(topic));
            firstDelivery = pollRecord(consumer, Duration.ofSeconds(20));
            assertThat(firstDelivery).isNotNull();
            assertThat(firstDelivery.value()).isEqualTo(payload);
            // Simulate process termination before the offset is committed.
        }

        try (Consumer<String, String> consumer = new KafkaConsumer<>(
                recoveryConsumerProperties(bootstrapServers, groupId))) {
            consumer.subscribe(List.of(topic));
            ConsumerRecord<String, String> redelivery = pollRecord(consumer, Duration.ofSeconds(20));
            assertThat(redelivery).isNotNull();
            assertThat(redelivery.value()).isEqualTo(payload);
            assertThat(redelivery.topic()).isEqualTo(firstDelivery.topic());
            assertThat(redelivery.partition()).isEqualTo(firstDelivery.partition());
            assertThat(redelivery.offset()).isEqualTo(firstDelivery.offset());
            TopicPartition partition = new TopicPartition(redelivery.topic(), redelivery.partition());
            consumer.commitSync(Map.of(partition, new OffsetAndMetadata(redelivery.offset() + 1)));
        }

        try (Consumer<String, String> consumer = new KafkaConsumer<>(
                recoveryConsumerProperties(bootstrapServers, groupId))) {
            consumer.subscribe(List.of(topic));
            ConsumerRecord<String, String> unexpected = pollRecord(consumer, Duration.ofSeconds(3));
            assertThat(unexpected)
                    .as("a committed offset must not be replayed when the same group restarts")
                    .isNull();
        }
    }

    private static ConsumerRecord<String, String> pollRecord(
            Consumer<String, String> consumer, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) return records.iterator().next();
        }
        return null;
    }

    private static Properties recoveryConsumerProperties(String bootstrapServers, String groupId) {
        Properties properties = consumerProperties(bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    private void send(String topic, String payload, String eventType) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties(bootstrapServers))) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, "connector-test", payload);
            record.headers().add(new RecordHeader("numou-event-type",
                    eventType.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        }
    }

    private static ConsumerRecord<String, String> pollDeadLetterContaining(
            String bootstrapServers, String marker, Duration timeout) {
        Properties properties = consumerProperties(bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "numou-event-dead-letter-" + UUID.randomUUID());
        try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(DEAD_LETTER_TOPIC));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(250));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(marker)) return record;
                }
            }
            return null;
        }
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Properties producerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return properties;
    }

    private static Properties consumerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "numou-event-dead-letter-acceptance-test");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }

    public static final class TestSaleCompleted implements tech.kayys.numou.event.domain.BusinessEvent {
        public String saleId;

        public TestSaleCompleted() {
        }

        TestSaleCompleted(String saleId) {
            this.saleId = saleId;
        }

        @Override
        public UUID eventId() {
            return UUID.nameUUIDFromBytes(saleId.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Instant occurredAt() {
            return Instant.EPOCH;
        }

        @Override
        public String eventType() {
            return EVENT_TYPE;
        }

        @Override
        public EventVersion eventVersion() {
            return EventVersion.V1;
        }
    }
}
