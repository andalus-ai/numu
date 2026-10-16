package tech.kayys.numou.foundation.application.query;

import io.smallrye.mutiny.Uni;

/**
 * Interceptor for query dispatching across the platform (Docs/Enhancement/enhance02.md §P2.4).
 *
 * <p>Middleware can inspect/modify execution context, perform validation, enforce security,
 * log metrics, or handle errors.
 */
@FunctionalInterface
public interface QueryMiddleware {

    /**
     * Intercepts a query invocation in the pipeline.
     *
     * @param context the query execution context
     * @param next the next invocation in the chain
     * @param <R> the result type
     * @return reactive Uni producing the result
     */
    <R> Uni<R> invoke(QueryContext context, QueryInvocation<R> next);
}
