package tech.kayys.numou.restaurant.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.function.Supplier;

/** One database transaction per use case (aggregate rows + outbox rows commit together). NOT COMPILED in sandbox. */
@ApplicationScoped
public class PgUnitOfWork implements UnitOfWork {

    @Override
    public <R> Uni<R> execute(Supplier<Uni<R>> work) {
        return Panache.withTransaction(work);
    }
}
