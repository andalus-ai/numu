package tech.kayys.syirkah.document.adapter.outbound.transaction;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;

import java.util.function.Supplier;

@ApplicationScoped
@DefaultBean
public class HibernateReactiveDocumentUnitOfWork implements UnitOfWork {
    @Override
    public <R> Uni<R> execute(Supplier<Uni<R>> work) {
        return Panache.withTransaction(work::get);
    }
}
