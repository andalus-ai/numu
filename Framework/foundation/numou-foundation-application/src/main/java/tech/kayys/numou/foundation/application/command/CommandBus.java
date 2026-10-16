package tech.kayys.numou.foundation.application.command;

import io.smallrye.mutiny.Uni;

/**
 * Universal application bus for dispatching {@link Command} instances through
 * the runtime middleware pipeline (Docs/Enhancement/enhance00.md §2.2, enhance02.md §P2.2).
 */
public interface CommandBus {

    /**
     * Dispatches a command using default/ambient execution context.
     *
     * @param command the command to execute
     * @param <C> the command type
     * @param <R> the expected result type
     * @return reactive Uni emitting the handler outcome
     */
    <C extends Command, R> Uni<R> dispatch(C command);

    /**
     * Dispatches a command within an explicit {@link CommandContext}.
     *
     * @param command the command to execute
     * @param context the explicit command context
     * @param <C> the command type
     * @param <R> the expected result type
     * @return reactive Uni emitting the handler outcome
     */
    <C extends Command, R> Uni<R> dispatch(C command, CommandContext context);
}
