package tech.kayys.numou.foundation.application.middleware;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandContext;
import tech.kayys.numou.foundation.application.command.CommandInvocation;
import tech.kayys.numou.foundation.application.command.CommandMiddleware;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;

/**
 * Middleware that wraps command execution within a transactional {@link UnitOfWork}.
 */
public final class TransactionMiddleware implements CommandMiddleware, OrderedMiddleware {

    private final UnitOfWork unitOfWork;

    public TransactionMiddleware(UnitOfWork unitOfWork) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public int order() {
        return TRANSACTION_ORDER;
    }

    @Override
    public <R> Uni<R> invoke(CommandContext context, CommandInvocation<R> next) {
        return unitOfWork.execute(next::proceed);
    }
}
