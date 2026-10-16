package tech.kayys.syirkah.accounting.application.cqrs;

import io.smallrye.mutiny.Uni;

/**
 * Decoupled in-memory Command Bus dispatching commands to registered handlers.
 */
public interface CommandBus {
    <C extends Command, R> Uni<R> dispatch(C command);
    <C extends Command, R> void registerHandler(Class<C> commandClass, CommandHandler<C, R> handler);
}
