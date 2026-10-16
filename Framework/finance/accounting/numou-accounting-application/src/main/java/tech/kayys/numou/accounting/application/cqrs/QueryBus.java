package tech.kayys.syirkah.accounting.application.cqrs;

import io.smallrye.mutiny.Uni;

/**
 * Decoupled in-memory Query Bus executing queries through registered read handlers.
 */
public interface QueryBus {
    <Q extends Query<R>, R> Uni<R> execute(Q query);
    <Q extends Query<R>, R> void registerHandler(Class<Q> queryClass, QueryHandler<Q, R> handler);
}
