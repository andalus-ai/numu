package tech.kayys.numou.foundation.application.tracing;

import tech.kayys.numou.foundation.application.context.ExecutionContext;

/**
 * Framework-neutral port for distributed tracing (enhance05.md §4).
 */
public interface Tracing {

    /**
     * Starts a trace span for an operation.
     *
     * @param name stable logical operation name (e.g., "command.CreateProduct")
     * @param kind the span kind
     * @param context the execution context metadata
     * @return active trace span
     */
    TraceSpan startSpan(String name, SpanKind kind, ExecutionContext context);

    /**
     * @return a no-op tracing instance
     */
    static Tracing noop() {
        return NoopTracing.INSTANCE;
    }
}
