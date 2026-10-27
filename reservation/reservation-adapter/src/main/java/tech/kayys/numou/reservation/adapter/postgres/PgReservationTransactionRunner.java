package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;

import java.util.Objects;
import java.util.function.Function;

/**
 * Runs reservation persistence work on one PostgreSQL connection and transaction.
 *
 * <p>Every reservation, ledger, idempotency, and outbox statement participating in a
 * booking must use the supplied connection. Repositories that call PgPool directly
 * are outside this transaction and must not be used for participating writes.</p>
 *
 * <p>This is deliberately a small infrastructure primitive rather than an implicit CDI
 * bean: the application composition root must wire transaction-bound repository instances
 * to the callback for the lifetime of the transaction.</p>
 */
public final class PgReservationTransactionRunner {
    private final PgPool pool;

    public PgReservationTransactionRunner(PgPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    public <T> Uni<T> inTransaction(Function<SqlConnection, Uni<T>> work) {
        Objects.requireNonNull(work, "work");
        return pool.withTransaction(connection -> {
            Uni<T> result = work.apply(connection);
            return result != null
                    ? result
                    : Uni.createFrom().failure(new IllegalStateException(
                            "Reservation transaction callback returned null Uni"));
        });
    }
}
