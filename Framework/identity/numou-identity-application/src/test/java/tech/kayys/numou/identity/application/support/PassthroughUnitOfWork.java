package tech.kayys.syirkah.identity.application.support;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;

import java.util.function.Supplier;

/**
 * Test double that runs the work directly, with no real transaction -
 * fine for exercising handler logic, not a substitute for testing the
 * real HibernateReactiveUnitOfWork against Postgres.
 */
public final class PassthroughUnitOfWork implements UnitOfWork {

    @Override
    public <R> Uni<R> execute(Supplier<Uni<R>> work) {
        return work.get();
    }

}
