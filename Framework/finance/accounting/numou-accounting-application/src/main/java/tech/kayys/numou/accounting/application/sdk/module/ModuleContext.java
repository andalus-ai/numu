package tech.kayys.syirkah.accounting.application.sdk.module;

import tech.kayys.syirkah.accounting.application.cqrs.Command;
import tech.kayys.syirkah.accounting.application.cqrs.CommandHandler;
import tech.kayys.syirkah.accounting.application.cqrs.Query;
import tech.kayys.syirkah.accounting.application.cqrs.QueryHandler;
import tech.kayys.syirkah.accounting.application.projection.Projection;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;

/**
 * Context passed to FinancialModule.register(...) during bootstrap.
 * Modules extend the platform by registering their handlers and projections here.
 */
public interface ModuleContext {

    /** Register a command handler so the CommandBus can dispatch to it. */
    <C extends Command, R> void registerCommand(
            Class<C> commandType,
            CommandHandler<C, R> handler);

    /** Register a query handler so the QueryBus can dispatch to it. */
    <Q extends Query<R>, R> void registerQuery(
            Class<Q> queryType,
            QueryHandler<Q, R> handler);

    /** Register an event projection to keep read models up to date. */
    void registerProjection(Projection<? extends AccountingEvent> projection);
}
