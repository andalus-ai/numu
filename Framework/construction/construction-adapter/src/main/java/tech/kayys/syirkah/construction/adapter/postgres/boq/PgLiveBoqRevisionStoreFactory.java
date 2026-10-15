package tech.kayys.syirkah.construction.adapter.postgres.boq;

import io.vertx.mutiny.sqlclient.SqlConnection;

/**
 * Connection-bound adapter for the project's existing mutable BOQ and BOQ-item tables.
 * Implement this against the real schema; methods must use only the supplied connection.
 */
public interface PgLiveBoqRevisionStoreFactory {
    PgLiveBoqRevisionStore create(SqlConnection connection);
}
