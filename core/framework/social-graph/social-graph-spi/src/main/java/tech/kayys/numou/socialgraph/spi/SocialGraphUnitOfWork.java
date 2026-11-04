package tech.kayys.numou.socialgraph.spi;

import io.smallrye.mutiny.Uni;

import java.util.function.Function;

/** Executes graph state, idempotency, audit and outbox writes in one atomic transaction. */
public interface SocialGraphUnitOfWork {
    <T> Uni<T> execute(Function<SocialGraphTransaction, T> work);
}
