package tech.kayys.numou.foundation.application.query;

import io.smallrye.mutiny.Uni;

/**
 * Represents the continuation of the query execution pipeline in a middleware chain.
 *
 * @param <R> the result type produced by the pipeline
 */
@FunctionalInterface
public interface QueryInvocation<R> {

    /**
     * Continues execution down the query middleware chain toward the handler.
     *
     * @return a {@link Uni} emitting the result
     */
    Uni<R> proceed();
}
