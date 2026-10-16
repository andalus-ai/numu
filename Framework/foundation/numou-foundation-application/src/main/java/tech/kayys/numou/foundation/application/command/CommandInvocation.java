package tech.kayys.numou.foundation.application.command;

import io.smallrye.mutiny.Uni;

/**
 * Represents the continuation of the command execution pipeline in a middleware chain.
 *
 * @param <R> the result type produced by the pipeline
 */
@FunctionalInterface
public interface CommandInvocation<R> {

    /**
     * Continues execution down the middleware chain toward the handler.
     *
     * @return a {@link Uni} emitting the result
     */
    Uni<R> proceed();
}
