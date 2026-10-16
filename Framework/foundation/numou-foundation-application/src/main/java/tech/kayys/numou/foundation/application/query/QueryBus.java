package tech.kayys.numou.foundation.application.query;

import io.smallrye.mutiny.Uni;

/**
 * Port for dispatching queries to their registered handlers through a middleware pipeline.
 */
public interface QueryBus {

    /**
     * Dispatches a query using the ambient execution context.
     *
     * @param query the query to execute
     * @param <Q> the query type
     * @param <R> the result type
     * @return reactive Uni producing the result
     */
    <Q extends Query, R> Uni<R> dispatch(Q query);

    /**
     * Dispatches a query within an explicit {@link QueryContext}.
     *
     * @param query the query to execute
     * @param context the query context
     * @param <Q> the query type
     * @param <R> the result type
     * @return reactive Uni producing the result
     */
    <Q extends Query, R> Uni<R> dispatch(Q query, QueryContext context);
}
