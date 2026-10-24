package tech.kayys.numou.omnichannel.core;

/** Thread-safe metrics sink contract; adapters can bridge it to Micrometer or OpenTelemetry. */
public interface InboundEventMetrics {
    void recordOutcome(InboundWorkerDisposition disposition);

    void recordFailure(InboundWorkerDisposition disposition);
}
