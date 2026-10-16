package tech.kayys.syirkah.accounting.application.cqrs;

import io.smallrye.mutiny.Uni;

/**
 * Handler interface processing a Query and returning a reactive Uni of result type R.
 */
public interface QueryHandler<Q extends Query<R>, R> {
    Uni<R> handle(Q query);
}
