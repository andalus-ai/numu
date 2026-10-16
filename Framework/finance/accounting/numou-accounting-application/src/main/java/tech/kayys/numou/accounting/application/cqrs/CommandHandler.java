package tech.kayys.syirkah.accounting.application.cqrs;

import io.smallrye.mutiny.Uni;

/**
 * Handler interface processing a specific Command and returning a reactive Uni of result type R.
 */
public interface CommandHandler<C extends Command, R> {
    Uni<R> handle(C command);
}
