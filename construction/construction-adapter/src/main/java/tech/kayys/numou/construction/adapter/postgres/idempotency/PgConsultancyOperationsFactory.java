package tech.kayys.numou.construction.adapter.postgres.idempotency;

import io.vertx.mutiny.sqlclient.SqlConnection;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;

/** Creates consultancy operations bound to the command's transaction connection. */
public interface PgConsultancyOperationsFactory {
    TransactionalCommandContext.ConsultancyOperations create(SqlConnection connection);
}
