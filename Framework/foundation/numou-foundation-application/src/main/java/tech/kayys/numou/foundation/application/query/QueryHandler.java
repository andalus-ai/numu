package tech.kayys.numou.foundation.application.query;

import io.smallrye.mutiny.Uni;

/**
 * Handles exactly one query type and produces a read result.
 *
 * @param <Q> the query type
 * @param <R> the result type
 */
public interface QueryHandler<Q extends Query, R> {

    Uni<R> handle(Q query);

}
