package tech.kayys.numou.reliability.outbox;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEntryDiagnosticTest {
    record Sample(UUID eventId, Instant occurredAt) implements BusinessEvent {
        @Override public String eventType() { return "test.outbox.diagnostic"; }
        @Override public EventVersion eventVersion() { return EventVersion.V1; }
    }

    private OutboxEntry entry() {
        var metadata = EventMetadata.of(UUID.randomUUID(), null, "test", CorrelationId.generate());
        return OutboxEntry.pending(EventEnvelope.of(metadata, new Sample(UUID.randomUUID(), Instant.now())));
    }

    @Test
    void failureDiagnosticSurvivesRetryAndIsClearedAfterPublication() {
        var failed = entry().recordFailure(Instant.now().plusSeconds(5), "IllegalStateException: broker unavailable");
        assertThat(failed.lastError()).isEqualTo("IllegalStateException: broker unavailable");
        assertThat(failed.attempts()).isEqualTo(1);

        var retried = failed.recordFailure(Instant.now().plusSeconds(10), "TimeoutException: broker timed out");
        assertThat(retried.lastError()).isEqualTo("TimeoutException: broker timed out");

        var published = retried.markPublished();
        assertThat(published.published()).isTrue();
        assertThat(published.lastError()).isNull();
    }

    @Test
    void diagnosticIsBoundedAndSixArgumentConstructorRemainsCompatible() {
        var original = entry();
        var compatible = new OutboxEntry(original.entryId(), original.envelope(), original.createdAt(),
                original.availableAt(), original.attempts(), original.published());
        assertThat(compatible.lastError()).isNull();

        var failed = original.recordFailure(Instant.now().plusSeconds(1), "x".repeat(OutboxEntry.MAX_ERROR_LENGTH + 20));
        assertThat(failed.lastError()).hasSize(OutboxEntry.MAX_ERROR_LENGTH);
    }
}