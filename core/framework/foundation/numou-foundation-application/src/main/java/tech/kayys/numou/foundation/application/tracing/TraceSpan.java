package tech.kayys.numou.foundation.application.tracing;

/**
 * Handle to an active distributed trace span (enhance05.md §4).
 */
public interface TraceSpan extends AutoCloseable {

    /**
     * Marks the span as successfully completed.
     */
    void success();

    /**
     * Records a failure and marks the span status as ERROR.
     *
     * @param error the failure cause
     */
    void failure(Throwable error);

    /**
     * Records an attribute key-value pair on the span.
     *
     * @param key attribute name
     * @param value attribute value
     */
    void attribute(String key, String value);

    /**
     * Closes the span and ends its timing.
     */
    @Override
    void close();
}
