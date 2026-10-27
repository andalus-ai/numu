package tech.kayys.numou.reservation.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import tech.kayys.numou.reservation.spi.DomainEventSink;
import tech.kayys.numou.reservation.spi.ReservationTransactionContext;
import tech.kayys.numou.reservation.spi.ReservationTransactionRunner;

import java.util.Objects;
import java.util.function.Function;

/**
 * Runs reservation persistence work on one PostgreSQL connection and transaction.
 *
 * <p>The transaction-aware constructor requires a sink factory that creates a sink backed
 * by the supplied connection (normally an outbox writer). A pool-backed or remote publisher
 * is not transactionally safe and must not be supplied for commands that require atomic events.</p>
 *
 * <p>This is deliberately not an implicit CDI bean. The composition root owns construction.</p>
 */
public final class PgReservationTransactionRunner implements ReservationTransactionRunner {
    private final PgPool pool;
    private final Function<SqlConnection, DomainEventSink> eventSinkFactory;

    /**
     * Connection-only compatibility constructor for infrastructure callers that use
     * {@link #inTransaction(Function)} directly. It cannot be used by ReservationService.
     */
    public PgReservationTransactionRunner(PgPool pool) {
        this(pool, null);
    }

    public PgReservationTransactionRunner(PgPool pool,
                                          Function<SqlConnection, DomainEventSink> eventSinkFactory) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.eventSinkFactory = eventSinkFactory;
    }

    /**
     * Executes a command with reservation and ledger repositories bound to the same connection.
     */
    @Override
    public <T> Uni<T> execute(Function<ReservationTransactionContext, Uni<T>> work) {
        Objects.requireNonNull(work, "work");
        if (eventSinkFactory == null) {
            return Uni.createFrom().failure(new IllegalStateException(
                    "ReservationService requires a transaction-bound event sink factory"));
        }
        return pool.withTransaction(connection -> {
            final ReservationTransactionContext context = new ReservationTransactionContext(
                    new PgReservationRepository(connection),
                    new PgLedgerRepository(connection),
                    Objects.requireNonNull(eventSinkFactory.apply(connection),
                            "eventSinkFactory returned null"));
            Uni<T> result = work.apply(context);
            return result != null ? result : Uni.createFrom().failure(
                    new IllegalStateException("Reservation transaction callback returned null Uni"));
        });
    }

    /** Low-level transaction callback for infrastructure-only uses. */
    public <T> Uni<T> inTransaction(Function<SqlConnection, Uni<T>> work) {
        Objects.requireNonNull(work, "work");
        return pool.withTransaction(connection -> {
            Uni<T> result = work.apply(connection);
            return result != null ? result : Uni.createFrom().failure(
                    new IllegalStateException("Reservation transaction callback returned null Uni"));
        });
    }
}
