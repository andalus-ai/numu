package tech.kayys.numou.event.adapter.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.IncomingKafkaRecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KafkaEventSubscriberAdapterTest {

    private static final String EVENT_TYPE = "pos.sale.completed";
    private static final String PAYLOAD = "{\"wireFormatVersion\":1}";

    @Test
    void acknowledgesOnlyAfterAllHandlersComplete() {
        EventEnvelope<TestEvent> envelope = envelope();
        KafkaEventSubscriberAdapter adapter = new KafkaEventSubscriberAdapter(serializer(envelope));
        AtomicInteger order = new AtomicInteger();
        adapter.subscribe(EVENT_TYPE, ignored -> {
            assertThat(order.incrementAndGet()).isEqualTo(1);
            return Uni.createFrom().voidItem();
        });
        adapter.subscribe(EVENT_TYPE, ignored -> {
            assertThat(order.incrementAndGet()).isEqualTo(2);
            return Uni.createFrom().voidItem();
        });
        Message<String> message = message(PAYLOAD, EVENT_TYPE);

        adapter.consume(message).await().indefinitely();

        assertThat(order).hasValue(2);
        verify(message, times(1)).ack();
        verify(message, never()).nack(any());
    }

    @Test
    void nacksHandlerFailureAndPropagatesOriginalFailure() {
        EventEnvelope<TestEvent> envelope = envelope();
        KafkaEventSubscriberAdapter adapter = new KafkaEventSubscriberAdapter(serializer(envelope));
        IllegalStateException failure = new IllegalStateException("temporary downstream failure");
        adapter.subscribe(EVENT_TYPE, ignored -> Uni.createFrom().failure(failure));
        Message<String> message = message(PAYLOAD, EVENT_TYPE);

        assertThatThrownBy(() -> adapter.consume(message).await().indefinitely())
                .isSameAs(failure);

        verify(message, never()).ack();
        verify(message, times(1)).nack(failure);
    }

    @Test
    void canProcessRedeliveredEventAfterTransientHandlerFailure() {
        EventEnvelope<TestEvent> envelope = envelope();
        KafkaEventSubscriberAdapter adapter = new KafkaEventSubscriberAdapter(serializer(envelope));
        AtomicInteger attempts = new AtomicInteger();
        adapter.subscribe(EVENT_TYPE, ignored -> attempts.incrementAndGet() == 1
                ? Uni.createFrom().failure(new IllegalStateException("temporary"))
                : Uni.createFrom().voidItem());
        Message<String> firstDelivery = message(PAYLOAD, EVENT_TYPE);
        Message<String> redelivery = message(PAYLOAD, EVENT_TYPE);

        assertThatThrownBy(() -> adapter.consume(firstDelivery).await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
        adapter.consume(redelivery).await().indefinitely();

        assertThat(attempts).hasValue(2);
        verify(firstDelivery, never()).ack();
        verify(firstDelivery, times(1)).nack(any());
        verify(redelivery, times(1)).ack();
        verify(redelivery, never()).nack(any());
    }

    @Test
    void doesNotNackWhenBrokerAcknowledgementItselfFails() {
        EventEnvelope<TestEvent> envelope = envelope();
        KafkaEventSubscriberAdapter adapter = new KafkaEventSubscriberAdapter(serializer(envelope));
        adapter.subscribe(EVENT_TYPE, ignored -> Uni.createFrom().voidItem());
        Message<String> message = message(PAYLOAD, EVENT_TYPE);
        RuntimeException ackFailure = new RuntimeException("ack channel failed");
        when(message.ack()).thenReturn(CompletableFuture.failedFuture(ackFailure));

        assertThatThrownBy(() -> adapter.consume(message).await().indefinitely())
                .isSameAs(ackFailure);

        verify(message, times(1)).ack();
        verify(message, never()).nack(any());
    }

    @Test
    void nacksMalformedRecordWithoutInvokingHandler() {
        EventEnvelope<TestEvent> envelope = envelope();
        KafkaEventSubscriberAdapter adapter = new KafkaEventSubscriberAdapter(serializer(envelope));
        AtomicInteger handled = new AtomicInteger();
        adapter.subscribe(EVENT_TYPE, ignored -> {
            handled.incrementAndGet();
            return Uni.createFrom().voidItem();
        });
        Message<String> message = message(PAYLOAD, null);

        assertThatThrownBy(() -> adapter.consume(message).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(handled).hasValue(0);
        verify(message, never()).ack();
        verify(message, times(1)).nack(any());
    }

    private static Message<String> message(String payload, String eventType) {
        @SuppressWarnings("unchecked")
        Message<String> message = mock(Message.class);
        @SuppressWarnings("unchecked")
        IncomingKafkaRecordMetadata<String, String> metadata =
                mock(IncomingKafkaRecordMetadata.class);
        RecordHeaders headers = new RecordHeaders();
        if (eventType != null) {
            headers.add("numou-event-type", eventType.getBytes(StandardCharsets.UTF_8));
        }
        when(metadata.getHeaders()).thenReturn(headers);
        doReturn(Optional.of(metadata)).when(message)
                .getMetadata(IncomingKafkaRecordMetadata.class);
        when(message.getPayload()).thenReturn(payload);
        when(message.ack()).thenReturn(CompletableFuture.completedFuture(null));
        when(message.nack(any())).thenReturn(CompletableFuture.completedFuture(null));
        return message;
    }

    private static EventSerializerPort serializer(EventEnvelope<TestEvent> envelope) {
        return new EventSerializerPort() {
            @Override
            public String serialize(EventEnvelope<? extends BusinessEvent> event) {
                return PAYLOAD;
            }

            @Override
            public EventEnvelope<? extends BusinessEvent> deserialize(String payload, String eventType) {
                assertThat(eventType).isEqualTo(EVENT_TYPE);
                return envelope;
            }
        };
    }

    private static EventEnvelope<TestEvent> envelope() {
        EventMetadata metadata = new EventMetadata(
                EventId.generate(), CorrelationId.generate(), null,
                UUID.randomUUID(), UUID.randomUUID(), "pos-test",
                java.time.Instant.now().toString(), null);
        return EventEnvelope.of(metadata, new TestEvent());
    }

    private static final class TestEvent implements BusinessEvent {
        @Override public String eventType() { return EVENT_TYPE; }
        @Override public EventVersion eventVersion() { return EventVersion.V1; }
    }
}
