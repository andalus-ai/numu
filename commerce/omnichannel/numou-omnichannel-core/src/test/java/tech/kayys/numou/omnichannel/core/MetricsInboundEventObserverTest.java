package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsInboundEventObserverTest {
    @Test
    void countsOutcomesAndFailuresWithoutExposingErrorLabels() {
        InMemoryInboundEventMetrics metrics = new InMemoryInboundEventMetrics();
        MetricsInboundEventObserver observer = new MetricsInboundEventObserver(metrics);

        observer.onOutcome(InboundWorkerDisposition.PROCESSED);
        observer.onOutcome(InboundWorkerDisposition.DEAD_LETTERED);
        observer.onFailure("provider-secret-specific-error-123", InboundWorkerDisposition.DEAD_LETTERED);

        InboundEventMetricsSnapshot snapshot = metrics.snapshot();
        assertThat(snapshot.outcomes()).containsEntry(InboundWorkerDisposition.PROCESSED, 1L)
                .containsEntry(InboundWorkerDisposition.DEAD_LETTERED, 1L);
        assertThat(snapshot.failures()).containsEntry(InboundWorkerDisposition.DEAD_LETTERED, 1L);
        assertThat(snapshot.toString()).doesNotContain("provider-secret-specific-error-123");
    }
}
