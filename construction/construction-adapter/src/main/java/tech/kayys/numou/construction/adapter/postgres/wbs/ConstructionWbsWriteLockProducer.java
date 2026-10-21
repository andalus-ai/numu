package tech.kayys.numou.construction.adapter.postgres.wbs;

import io.vertx.mutiny.pgclient.PgPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.scope.adapter.postgres.PgAdvisoryBreakdownStructureWriteLock;
import tech.kayys.numou.scope.spi.BreakdownStructureWriteLock;

/**
 * Binds the neutral scope write-lock port to PostgreSQL advisory locking for
 * deployed construction workflows. All construction WBS structural writers
 * must inject this port rather than instantiate an in-process lock.
 */
@ApplicationScoped
public class ConstructionWbsWriteLockProducer {

    @Produces
    @ApplicationScoped
    BreakdownStructureWriteLock breakdownStructureWriteLock(PgPool pool) {
        return new PgAdvisoryBreakdownStructureWriteLock(pool);
    }
}
