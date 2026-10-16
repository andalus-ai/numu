package tech.kayys.numou.foundation.application.transaction;

import io.smallrye.mutiny.Uni;

/**
 * Lifecycle synchronization callbacks for unit of work operations (enhance03.md §P2-10).
 */
public interface UnitOfWorkSynchronization {

    /**
     * Invoked before database commit, after application logic succeeds.
     * Use for event harvesting, outbox writing, and pre-commit validation.
     *
     * @param context the current unit of work context
     * @return reactive Uni completing when before-commit processing finishes
     */
    default Uni<Void> beforeCommit(UnitOfWorkContext context) {
        return Uni.createFrom().voidItem();
    }

    /**
     * Invoked after the database transaction successfully commits.
     * Use for post-commit actions such as notifications, metrics, and async triggers.
     *
     * @param context the current unit of work context
     * @return reactive Uni completing when after-commit processing finishes
     */
    default Uni<Void> afterCommit(UnitOfWorkContext context) {
        return Uni.createFrom().voidItem();
    }

    /**
     * Invoked after the unit of work has rolled back due to an error.
     *
     * @param context the current unit of work context
     * @param cause the failure cause
     * @return reactive Uni completing when rollback processing finishes
     */
    default Uni<Void> afterRollback(UnitOfWorkContext context, Throwable cause) {
        return Uni.createFrom().voidItem();
    }
}
