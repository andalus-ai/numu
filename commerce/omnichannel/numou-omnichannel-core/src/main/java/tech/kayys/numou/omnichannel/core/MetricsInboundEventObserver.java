package tech.kayys.numou.omnichannel.core;

import java.util.Objects;

/** Worker observer adapter that intentionally ignores arbitrary error strings. */
public final class MetricsInboundEventObserver implements InboundEventObserver {
    private final InboundEventMetrics metrics;

    public MetricsInboundEventObserver(InboundEventMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public void onOutcome(InboundWorkerDisposition disposition) {
        metrics.recordOutcome(disposition);
    }

    @Override
    public void onFailure(String sanitizedErrorCode, InboundWorkerDisposition disposition) {
        // Error codes are deliberately not used as metric dimensions.
        metrics.recordFailure(disposition);
    }
}
