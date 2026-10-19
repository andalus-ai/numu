package tech.kayys.numou.foundation.application.tracing;

import tech.kayys.numou.foundation.application.context.ExecutionContext;

/**
 * No-operation implementation of {@link Tracing} and {@link TraceSpan}.
 */
public final class NoopTracing implements Tracing {

    public static final NoopTracing INSTANCE = new NoopTracing();
    private static final TraceSpan NOOP_SPAN = new NoopTraceSpan();

    private NoopTracing() {}

    @Override
    public TraceSpan startSpan(String name, SpanKind kind, ExecutionContext context) {
        return NOOP_SPAN;
    }

    private static final class NoopTraceSpan implements TraceSpan {
        @Override
        public void success() {}

        @Override
        public void failure(Throwable error) {}

        @Override
        public void attribute(String key, String value) {}

        @Override
        public void close() {}
    }
}
