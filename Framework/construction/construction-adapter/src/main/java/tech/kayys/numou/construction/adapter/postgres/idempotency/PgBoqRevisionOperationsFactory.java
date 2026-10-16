package tech.kayys.numou.construction.adapter.postgres.idempotency;

import io.vertx.mutiny.sqlclient.SqlConnection;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;

/**
 * Creates BOQ revision operations bound to the supplied connection.
 *
 * <p>The implementation must not use PgPool or acquire another connection.
 * Every query it performs must use the supplied transaction-bound connection.</p>
 */
public interface PgBoqRevisionOperationsFactory {
    TransactionalCommandContext.BoqRevisionOperations create(SqlConnection connection);
}
