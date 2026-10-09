package tech.kayys.syirkah.observability.adapter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.observability.TracingPort;

import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link TracingPort} on the OpenTelemetry API. The span is ended exactly once on success, failure or cancellation,
 * marked ERROR on failure, and the original failure is re-thrown unchanged. Quarkus stores the current OTel context on
 * the request's Vert.x duplicated context, so {@code makeCurrent()} parents spans created further down the Mutiny chain.
 * NOT COMPILED in sandbox.
 */
@ApplicationScoped
public class OtelTracingAdapter implements TracingPort {

    @Inject
    Tracer tracer;

    @Override
    public <T> Uni<T> inSpan(String spanName, Map<String, String> attributes, Supplier<Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            final Span span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.INTERNAL).startSpan();
            attributes.forEach(span::setAttribute);
            final Uni<T> inner;
            try (Scope ignored = span.makeCurrent()) {
                inner = work.get();
            } catch (RuntimeException e) {
                fail(span, e);
                span.end();
                throw e;
            }
            return inner
                    .onFailure().invoke(t -> fail(span, t))
                    .onTermination().invoke((item, failure, cancelled) -> {
                        if (Boolean.TRUE.equals(cancelled)) {
                            span.setAttribute("cancelled", true);
                        }
                        span.end();
                    });
        });
    }

    private static void fail(Span span, Throwable t) {
        span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
        span.recordException(t);
    }
}
