package tech.kayys.numou.reliability.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.reliability.application.outbox.OutboxRelayService;
import tech.kayys.numou.reliability.outbox.OutboxEntry;
import tech.kayys.numou.reliability.retry.RetryPolicy;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayDiagnosticTest {
    record Sample(UUID eventId, Instant occurredAt) implements BusinessEvent {
        @Override public String eventType() { return "test.outbox.diagnostic"; }
        @Override public EventVersion eventVersion() { return EventVersion.V1; }
    }

    @Test
    void relayPersistsFailureDetailsAcrossRetryAndClearsThemAfterAck() {
        var store = new ReliabilityApplicationTest.MemOutbox();
        var publisher = new ReliabilityApplicationTest.ScriptedPublisher();
        var metadata = EventMetadata.of(UUID.randomUUID(), null, "test", CorrelationId.generate());
        var entry = OutboxEntry.pending(EventEnvelope.of(metadata, new Sample(UUID.randomUUID(), Instant.now())));
        store.append(java.util.List.of(entry)).await().indefinitely();

        publisher.failures.add(new IllegalStateException("broker unavailable"));
        var relay = new OutboxRelayService(store, publisher, RetryPolicy.DEFAULT, 5,
                () -> store.now, () -> 0.5, OutboxRelayService.defaultClassifier());

        assertThat(relay.runOnce().await().indefinitely()).isZero();
        var failed = store.rows.get(entry.entryId());
        assertThat(failed.attempts()).isEqualTo(1);
        assertThat(failed.lastError()).isEqualTo("IllegalStateException: broker unavailable");

        store.now = failed.availableAt().plusSeconds(1);
        assertThat(relay.runOnce().await().indefinitely()).isEqualTo(1);
        var published = store.rows.get(entry.entryId());
        assertThat(published.published()).isTrue();
        assertThat(published.lastError()).isNull();
    }
}