package tech.kayys.numou.foundation.application.tracing;

/**
 * Framework-neutral representation of span kind (enhance05.md §5).
 */
public enum SpanKind {
    INTERNAL,
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER
}
