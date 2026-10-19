package tech.kayys.numou.crm.infrastructure.transaction;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.function.Supplier;

/**
 * Panache-backed {@link UnitOfWork}. Lets a handler run the aggregate write and
 * the transactional-outbox insert inside a single reactive transaction.
 */
@ApplicationScoped
public class CrmUnitOfWork implements UnitOfWork {

    @Override
    public <R> Uni<R> execute(Supplier<Uni<R>> work) {
        return Panache.withTransaction(work);
    }
}
