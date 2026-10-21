package tech.kayys.numou.scope.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.spi.BreakdownStructureWriteLock;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Coordinates structure mutations across application instances using a
 * PostgreSQL session-level advisory lock.
 *
 * <p>A dedicated connection is retained for the full asynchronous operation.
 * All writers must use this implementation (or the same advisory-lock key
 * convention) for cross-instance serialization to hold.</p>
 */
public final class PgAdvisoryBreakdownStructureWriteLock implements BreakdownStructureWriteLock {
    private static final String LOCK_SQL =
            "SELECT pg_advisory_lock(hashtextextended($1, 0))";
    private static final String UNLOCK_SQL =
            "SELECT pg_advisory_unlock(hashtextextended($1, 0))";

    private final PgPool pool;

    public PgAdvisoryBreakdownStructureWriteLock(PgPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public <T> CompletionStage<T> withLock(
            BreakdownStructureId structureId,
            Supplier<CompletionStage<T>> operation) {
        Objects.requireNonNull(structureId, "structureId");
        Objects.requireNonNull(operation, "operation");
        String lockKey = "numou:scope:structure:" + structureId.value();

        return pool.getConnection()
                .flatMap(connection -> acquireAndRun(connection, lockKey, operation)
                        .eventually(connection::close))
                .subscribeAsCompletionStage();
    }

    private <T> Uni<T> acquireAndRun(
            SqlConnection connection,
            String lockKey,
            Supplier<CompletionStage<T>> operation) {
        return connection.preparedQuery(LOCK_SQL)
                .execute(Tuple.of(lockKey))
                .flatMap(ignored -> {
                    final CompletionStage<T> stage;
                    try {
                        stage = Objects.requireNonNull(
                                operation.get(), "Locked operation returned a null CompletionStage");
                    } catch (Throwable failure) {
                        return release(connection, lockKey)
                                .onItemOrFailure().transformToUni((nothing, releaseFailure) -> {
                                    if (releaseFailure != null) failure.addSuppressed(releaseFailure);
                                    return Uni.createFrom().failure(failure);
                                });
                    }
                    return Uni.createFrom().completionStage(stage)
                            .eventually(() -> release(connection, lockKey));
                });
    }

    private Uni<Void> release(SqlConnection connection, String lockKey) {
        return connection.preparedQuery(UNLOCK_SQL)
            .execute(Tuple.of(lockKey))
            .replaceWithVoid();
    }
}
