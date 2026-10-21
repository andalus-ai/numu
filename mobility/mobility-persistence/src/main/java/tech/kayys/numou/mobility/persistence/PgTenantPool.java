package tech.kayys.numou.mobility.persistence;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;

import java.util.UUID;
import java.util.function.Function;

/**
 * Runs a statement on the Vert.x pool inside a transaction whose first step sets {@code app.tenant_id} (transaction-local), so
 * row-level security applies exactly as it does for the Hibernate-session repositories. Used for the high-frequency, non-
 * transactional-with-aggregates tables (driver locations, demand, surge). NOT COMPILED in the authoring sandbox.
 */
final class PgTenantPool {

    private PgTenantPool() { }

    static <T> Uni<T> run(PgPool pool, UUID tenantId, Function<SqlConnection, Uni<T>> work) {
        return pool.withTransaction(conn -> conn.preparedQuery("SELECT set_config('app.tenant_id', $1, true)")
                .execute(Tuple.of(tenantId.toString()))
                .flatMap(ignored -> work.apply(conn)));
    }
}
