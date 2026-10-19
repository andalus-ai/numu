package tech.kayys.numou.foundation.application.tracing;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.middleware.OrderedMiddleware;
import tech.kayys.numou.foundation.application.query.QueryContext;
import tech.kayys.numou.foundation.application.query.QueryInvocation;
import tech.kayys.numou.foundation.application.query.QueryMiddleware;

import java.util.Objects;

/**
 * Middleware that wraps query execution in a distributed trace span (enhance05.md §9).
 */
public final class QueryTracingMiddleware implements QueryMiddleware, OrderedMiddleware {

    private final Tracing tracing;

    public QueryTracingMiddleware(Tracing tracing) {
        this.tracing = Objects.requireNonNull(tracing, "tracing cannot be null");
    }

    @Override
    public int order() {
        return TRACING_ORDER;
    }

    @Override
    public <R> Uni<R> invoke(QueryContext context, QueryInvocation<R> next) {
        String queryName = context != null && context.query() != null
                ? context.query().getClass().getSimpleName()
                : "UnknownQuery";

        var span = tracing.startSpan(
                "query." + queryName,
                SpanKind.INTERNAL,
                context != null ? context.executionContext() : null
        );

        return next.proceed()
                .invoke(res -> span.success())
                .onFailure()
                .invoke(span::failure)
                .eventually(span::close);
    }
}
