package tech.kayys.numou.socialgraph.spi;

import java.util.function.Function;

/** Executes graph state, idempotency and outbox writes in one atomic transaction. */
public interface SocialGraphUnitOfWork {
    <T> T execute(Function<SocialGraphTransaction, T> work);
}
