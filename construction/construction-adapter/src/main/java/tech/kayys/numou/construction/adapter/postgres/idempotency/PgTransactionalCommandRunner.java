package tech.kayys.numou.construction.adapter.postgres.idempotency;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandRunner;

import java.util.Objects;
import java.util.function.Function;

/**
 * Runs a command callback in one PostgreSQL transaction. Mutiny's withTransaction
 * commits on successful completion and rolls back when the returned Uni fails.
 *
 * <p>Deliberately not a CDI bean: this adapter module is a plain library jar (no
 * quarkus-maven-plugin), and the {@link PgBoqRevisionOperationsFactory} dependency is
 * application-specific. Annotating this class made every Quarkus boot fail with
 * UnsatisfiedResolutionException because no such bean is ever produced. Callers
 * construct it explicitly, e.g. {@code new PgTransactionalCommandRunner(pool, factory)}.
 */
public class PgTransactionalCommandRunner implements TransactionalCommandRunner {
    private final PgPool pool;
    private final PgBoqRevisionOperationsFactory boqOperationsFactory;
    private final PgConsultancyOperationsFactory consultancyOperationsFactory;

    public PgTransactionalCommandRunner(PgPool pool,
                                        PgBoqRevisionOperationsFactory boqOperationsFactory) {
        this(pool, boqOperationsFactory, null);
    }

    public PgTransactionalCommandRunner(PgPool pool,
                                        PgBoqRevisionOperationsFactory boqOperationsFactory,
                                        PgConsultancyOperationsFactory consultancyOperationsFactory) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.boqOperationsFactory = Objects.requireNonNull(boqOperationsFactory, "boqOperationsFactory");
        this.consultancyOperationsFactory = consultancyOperationsFactory;
    }

    @Override
    public <T> Uni<T> inTransaction(Function<TransactionalCommandContext, Uni<T>> work) {
        Objects.requireNonNull(work, "work");
        return pool.withTransaction((SqlConnection connection) -> {
            TransactionalCommandContext context = new PgTransactionalCommandContext(
                    connection,
                    boqOperationsFactory.create(connection),
                    consultancyOperationsFactory == null
                            ? null : consultancyOperationsFactory.create(connection));
            Uni<T> result = work.apply(context);
            if (result == null) {
                return Uni.createFrom().failure(
                        new IllegalStateException("Transactional command callback returned null Uni"));
            }
            return result;
        });
    }
}
