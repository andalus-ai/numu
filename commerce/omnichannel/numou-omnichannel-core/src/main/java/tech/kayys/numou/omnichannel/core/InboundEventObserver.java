package tech.kayys.numou.omnichannel.core;

/**
 * Optional, provider-neutral worker telemetry hook.
 *
 * <p>Implementations should increment low-cardinality counters and must not attach
 * tenant IDs, external event IDs, payloads, or arbitrary exception messages as labels.
 * Observer failures are isolated from event processing.</p>
 */
public interface InboundEventObserver {
    InboundEventObserver NOOP = new InboundEventObserver() { };

    /** Called once for each completed worker poll or direct processing attempt. */
    default void onOutcome(InboundWorkerDisposition disposition) { }

    /** Called when an event is rescheduled or dead-lettered after a sanitized failure. */
    default void onFailure(String sanitizedErrorCode, InboundWorkerDisposition disposition) { }
}
