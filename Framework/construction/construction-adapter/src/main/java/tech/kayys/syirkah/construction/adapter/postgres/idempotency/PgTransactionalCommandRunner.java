package tech.kayys.syirkah.construction.adapter.postgres.idempotency;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.syirkah.construction.spi.idempotency.TransactionalCommandRunner;

import java.util.Objects;
import java.util.function.Function;

/**
 * Runs a command callback in one PostgreSQL transaction. Mutiny's withTransaction
 * commits on successful completion and rolls back when the returned Uni fails.
 */
@ApplicationScoped
public class PgTransactionalCommandRunner implements TransactionalCommandRunner {
    private final PgPool pool;
    private final PgBoqRevisionOperationsFactory boqOperationsFactory;

    @Inject
    public PgTransactionalCommandRunner(PgPool pool,
                                        PgBoqRevisionOperationsFactory boqOperationsFactory) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.boqOperationsFactory = Objects.requireNonNull(boqOperationsFactory, "boqOperationsFactory");
    }

    @Override
    public <T> Uni<T> inTransaction(Function<TransactionalCommandContext, Uni<T>> work) {
        Objects.requireNonNull(work, "work");
        return pool.withTransaction((SqlConnection connection) -> {
            TransactionalCommandContext context = new PgTransactionalCommandContext(
                    connection, boqOperationsFactory.create(connection));
            Uni<T> result = work.apply(context);
            if (result == null) {
                return Uni.createFrom().failure(
                        new IllegalStateException("Transactional command callback returned null Uni"));
            }
            return result;
        });
    }
}
