package tech.kayys.numou.construction.adapter.postgres.context;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL integration checks for transaction-local tenant context on a reused connection.
 * This verifies the database primitive only; it does not claim to exercise Numou authentication.
 */
@QuarkusTest
class PgTenantContextTransactionIntegrationTest {
    @Inject PgPool pool;

    @Test
    void committedTenantContextDoesNotLeakIntoNextTransactionOnSameConnection() {
        SqlConnection connection = pool.getConnection().await().indefinitely();
        try {
            assertEquals("tenant-alpha", pool.withTransaction(tx -> setTenant(tx, "tenant-alpha"))
                    .await().indefinitely());
            assertNoTenantContext(connection);
            assertEquals("tenant-beta", pool.withTransaction(tx -> setTenant(tx, "tenant-beta"))
                    .await().indefinitely());
            assertNoTenantContext(connection);
        } finally {
            connection.close().await().indefinitely();
        }
    }

    @Test
    void rolledBackTenantContextDoesNotLeakIntoNextTransactionOnSameConnection() {
        SqlConnection connection = pool.getConnection().await().indefinitely();
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                    pool.withTransaction(tx -> setTenant(tx, "tenant-rollback")
                                    .flatMap(tenant -> Uni.createFrom().<String>failure(
                                            new IllegalStateException("injected rollback"))))
                            .await().indefinitely());
            assertEquals("injected rollback", failure.getMessage());
            assertNoTenantContext(connection);
        } finally {
            connection.close().await().indefinitely();
        }
    }

    private static Uni<String> setTenant(SqlConnection connection, String tenantId) {
        return connection.preparedQuery("SELECT set_config('app.tenant_id', $1, true) AS tenant_id")
                .execute(Tuple.of(tenantId))
                .map(rows -> rows.iterator().next().getString("tenant_id"));
    }

    private static void assertNoTenantContext(SqlConnection connection) {
        Row row = connection.preparedQuery("SELECT current_setting('app.tenant_id', true) AS tenant_id")
                .execute().await().indefinitely().iterator().next();
        String value = row.getString("tenant_id");
        assertTrue(value == null || value.isBlank(),
                "transaction-local tenant context must be cleared before connection reuse; got: " + value);
    }
}
