package tech.kayys.syirkah.accounting.application.sdk.bootstrap;

import tech.kayys.syirkah.accounting.application.cqrs.CommandBus;
import tech.kayys.syirkah.accounting.application.cqrs.QueryBus;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.projection.ProjectionRegistry;
import tech.kayys.syirkah.accounting.application.sdk.module.FinancialModule;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PlatformPlugin;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PluginRegistry;

import java.util.List;

/**
 * Fully configured, self-contained accounting runtime.
 *
 * <p>Obtain via:
 * <pre>
 *   AccountingEngine engine = AccountingBootstrap.lite()
 *       .withPlugin(new AaoifiLitePlugin())
 *       .build();
 * </pre>
 *
 * <p>The engine exposes the CQRS buses, the outbox and the projection registry
 * without requiring a CDI/Spring container.
 */
public final class AccountingEngine {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final OutboxRepository outboxRepository;
    private final ProjectionRegistry projectionRegistry;
    private final PluginRegistry pluginRegistry;
    private final List<FinancialModule> modules;

    AccountingEngine(
            CommandBus commandBus,
            QueryBus queryBus,
            OutboxRepository outboxRepository,
            ProjectionRegistry projectionRegistry,
            PluginRegistry pluginRegistry,
            List<FinancialModule> modules) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
        this.outboxRepository = outboxRepository;
        this.projectionRegistry = projectionRegistry;
        this.pluginRegistry = pluginRegistry;
        this.modules = List.copyOf(modules);
    }

    public CommandBus commandBus() { return commandBus; }

    public QueryBus queryBus() { return queryBus; }

    public OutboxRepository outboxRepository() { return outboxRepository; }

    public ProjectionRegistry projectionRegistry() { return projectionRegistry; }

    public PluginRegistry pluginRegistry() { return pluginRegistry; }

    public List<FinancialModule> modules() { return modules; }

    /** Convenient flag: true when at least one plugin is active. */
    public boolean hasPlugins() { return !pluginRegistry.isEmpty(); }
}
