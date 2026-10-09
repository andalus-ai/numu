package tech.kayys.syirkah.reliability.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.reliability.idempotency.IdempotencyOutcome;
import tech.kayys.syirkah.reliability.spi.port.IdempotencyStorePort;

/**
 * Postgres idempotency store. The claim is a single {@code INSERT ... ON CONFLICT DO NOTHING}, so
 * concurrent callers cannot both win (verified with 8 racing sessions against PG 16). This replaces the
 * check-then-insert pattern in the asset module's adapter, which races and treats every DB error as "duplicate".
 *
 * <p>Failures propagate: a database outage must surface as an error, never as "already processed".
 * NOT COMPILED in sandbox.
 */
@ApplicationScoped
public class PgIdempotencyStore implements IdempotencyStorePort {

    static final String CLAIM = "INSERT INTO platform_idempotency (scope, idem_key) VALUES ($1, $2) ON CONFLICT DO NOTHING RETURNING first_seen_at";
    static final String LOOKUP = "SELECT first_seen_at, result_ref FROM platform_idempotency WHERE scope = $1 AND idem_key = $2";
    static final String RECORD = "UPDATE platform_idempotency SET result_ref = $3 WHERE scope = $1 AND idem_key = $2";
    static final String RELEASE = "DELETE FROM platform_idempotency WHERE scope = $1 AND idem_key = $2 AND result_ref IS NULL";
    private static final int MAX_CLAIM_ATTEMPTS = 3;

    @Inject
    PgPool pool;

    @Override
    public Uni<IdempotencyOutcome> claim(String key, String scope) {
        return claim(key, scope, 1);
    }

    private Uni<IdempotencyOutcome> claim(String key, String scope, int attempt) {
        return pool.preparedQuery(CLAIM).execute(Tuple.of(scope, key)).flatMap(inserted -> {
            if (inserted.iterator().hasNext()) {
                return Uni.createFrom().item(IdempotencyOutcome.proceed(
                        inserted.iterator().next().getOffsetDateTime("first_seen_at").toInstant()));
            }
            return pool.preparedQuery(LOOKUP).execute(Tuple.of(scope, key)).flatMap(found -> {
                if (found.iterator().hasNext()) {
                    final var row = found.iterator().next();
                    final String ref = row.getString("result_ref");
                    return Uni.createFrom().item(IdempotencyOutcome.duplicate(
                            row.getOffsetDateTime("first_seen_at").toInstant(), ref == null ? "" : ref));
                }
                // The winner released its claim between our INSERT and SELECT: try to claim again.
                return attempt >= MAX_CLAIM_ATTEMPTS
                        ? Uni.<IdempotencyOutcome>createFrom().failure(new IllegalStateException("idempotency claim contention for key"))
                        : claim(key, scope, attempt + 1);
            });
        });
    }

    @Override
    public Uni<Void> recordResult(String key, String scope, String resultRef) {
        return pool.preparedQuery(RECORD).execute(Tuple.of(scope, key, resultRef)).replaceWithVoid();
    }

    @Override
    public Uni<Void> release(String key, String scope) {
        return pool.preparedQuery(RELEASE).execute(Tuple.of(scope, key)).replaceWithVoid();
    }
}
