package tech.kayys.numou.foundation.application.transaction;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.domain.entity.AggregateRoot;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Standard implementation of {@link UnitOfWork} coordinating synchronization hooks,
 * aggregate event extraction, and optional event publication.
 */
public class DefaultUnitOfWork implements UnitOfWork {

    private static final ThreadLocal<UnitOfWorkContext> CURRENT_CONTEXT = new ThreadLocal<>();

    private final EventPublisher eventPublisher;

    public DefaultUnitOfWork() {
        this(null);
    }

    public DefaultUnitOfWork(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<UnitOfWorkContext> context() {
        return Optional.ofNullable(CURRENT_CONTEXT.get());
    }

    @Override
    public void register(UnitOfWorkSynchronization synchronization) {
        context().ifPresent(ctx -> ctx.register(synchronization));
    }

    @Override
    public void track(AggregateRoot<?> aggregate) {
        context().ifPresent(ctx -> ctx.track(aggregate));
    }

    @Override
    public <R> Uni<R> execute(Supplier<Uni<R>> work) {
        var existingContext = CURRENT_CONTEXT.get();
        if (existingContext != null) {
            // Nested UoW joins the current active context
            return Uni.createFrom().deferred(() -> work.get());
        }

        var context = new UnitOfWorkContext();
        CURRENT_CONTEXT.set(context);

        return Uni.createFrom().deferred(() -> work.get())
                .chain(result ->
                    executeBeforeCommit(context)
                        .replaceWith(result)
                )
                .call(result -> {
                    context.markCompleted();
                    return executeAfterCommit(context);
                })
                .onFailure()
                .call(error -> {
                    context.markRolledBack();
                    return executeAfterRollback(context, error);
                })
                .eventually(() -> CURRENT_CONTEXT.remove());
    }

    private Uni<Void> executeBeforeCommit(UnitOfWorkContext context) {
        context.collectEventsFromTouched();

        var synchs = context.synchronizations();
        if (synchs.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        Uni<Void> chain = Uni.createFrom().voidItem();
        for (var sync : synchs) {
            chain = chain.chain(() -> sync.beforeCommit(context));
        }
        return chain;
    }

    private Uni<Void> executeAfterCommit(UnitOfWorkContext context) {
        var synchs = context.synchronizations();
        Uni<Void> chain = Uni.createFrom().voidItem();

        for (var sync : synchs) {
            chain = chain.chain(() -> sync.afterCommit(context).onFailure().recoverWithNull());
        }

        if (eventPublisher != null && !context.domainEvents().isEmpty()) {
            chain = chain.chain(() -> eventPublisher.publish(context.domainEvents()).onFailure().recoverWithNull());
        }

        return chain;
    }

    private Uni<Void> executeAfterRollback(UnitOfWorkContext context, Throwable cause) {
        var synchs = context.synchronizations();
        if (synchs.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        Uni<Void> chain = Uni.createFrom().voidItem();
        for (var sync : synchs) {
            chain = chain.chain(() -> sync.afterRollback(context, cause).onFailure().recoverWithNull());
        }
        return chain;
    }
}
