package tech.kayys.syirkah.identity.adapter.outbound.transaction;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;

import java.util.function.Supplier;

@ApplicationScoped
public class HibernateReactiveUnitOfWork implements UnitOfWork {

    @Override
    public <R> Uni<R> execute(Supplier<Uni<R>> work) {
        return Panache.withTransaction(work::get);
    }

}
