package tech.kayys.numou.foundation.application.transaction;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.entity.AggregateRoot;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Transaction and aggregate lifecycle boundary port (enhance03.md §P2-10).
 *
 * <p>Coordinates transactional execution, aggregate tracking, event extraction,
 * and lifecycle synchronizations.
 */
public interface UnitOfWork {

    /**
     * Executes work atomically within a transaction boundary.
     *
     * @param work reactive work supplier
     * @param <R> the result type
     * @return reactive Uni emitting the result
     */
    <R> Uni<R> execute(Supplier<Uni<R>> work);

    /**
     * @return current active UnitOfWorkContext, if one exists
     */
    default Optional<UnitOfWorkContext> context() {
        return Optional.empty();
    }

    /**
     * Registers a lifecycle synchronization with the active unit of work.
     *
     * @param synchronization the synchronization callback
     */
    default void register(UnitOfWorkSynchronization synchronization) {
        context().ifPresent(ctx -> ctx.register(synchronization));
    }

    /**
     * Tracks an aggregate root instance touched in this unit of work.
     *
     * @param aggregate the aggregate root
     */
    default void track(AggregateRoot<?> aggregate) {
        context().ifPresent(ctx -> ctx.track(aggregate));
    }
}
