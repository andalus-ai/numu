package tech.kayys.numou.foundation.application.command;

import io.smallrye.mutiny.Uni;

/**
 * Interceptor for command dispatching across the platform (Docs/Enhancement/enhance02.md §P2.3).
 *
 * <p>Middleware can inspect/modify execution context, perform validation, enforce security,
 * establish transactions, log metrics, or handle errors.
 */
@FunctionalInterface
public interface CommandMiddleware {

    /**
     * Intercepts a command invocation in the pipeline.
     *
     * @param context the command execution context
     * @param next the next invocation in the chain
     * @param <R> the result type
     * @return reactive Uni producing the result
     */
    <R> Uni<R> invoke(CommandContext context, CommandInvocation<R> next);
}
