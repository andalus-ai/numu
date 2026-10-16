package tech.kayys.syirkah.accounting.application.sdk.bootstrap;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.compliance.ComplianceRule;
import tech.kayys.syirkah.accounting.application.compliance.ComplianceRulePipeline;
import tech.kayys.syirkah.accounting.application.cqrs.Command;
import tech.kayys.syirkah.accounting.application.cqrs.CommandHandler;
import tech.kayys.syirkah.accounting.application.cqrs.DefaultCommandBus;
import tech.kayys.syirkah.accounting.application.cqrs.DefaultQueryBus;
import tech.kayys.syirkah.accounting.application.cqrs.Query;
import tech.kayys.syirkah.accounting.application.cqrs.QueryHandler;
import tech.kayys.syirkah.accounting.application.outbox.OutboxEvent;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.projection.Projection;
import tech.kayys.syirkah.accounting.application.projection.ProjectionRegistry;
import tech.kayys.syirkah.accounting.application.sdk.module.FinancialModule;
import tech.kayys.syirkah.accounting.application.sdk.module.ModuleContext;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PlatformPlugin;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PluginContext;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PluginRegistry;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fluent builder that assembles an {@link AccountingEngine} without a DI container.
 */
public final class AccountingBootstrap {

    private OutboxRepository outboxRepository;
    private final List<PlatformPlugin> plugins = new ArrayList<>();
    private final List<FinancialModule> modules = new ArrayList<>();

    private AccountingBootstrap() {}

    public static AccountingBootstrap lite() {
        return new AccountingBootstrap();
    }

    public AccountingBootstrap withOutbox(OutboxRepository repo) {
        this.outboxRepository = repo;
        return this;
    }

    public AccountingBootstrap withPlugin(PlatformPlugin plugin) {
        plugins.add(plugin);
        return this;
    }

    public AccountingBootstrap withModule(FinancialModule module) {
        modules.add(module);
        return this;
    }

    public AccountingEngine build() {
        if (outboxRepository == null) {
            outboxRepository = new InMemoryOutboxRepositoryLite();
        }

        DefaultCommandBus commandBus = new DefaultCommandBus();
        DefaultQueryBus queryBus = new DefaultQueryBus();
        ProjectionRegistry projRegistry = new ProjectionRegistry();
        PluginRegistry pluginRegistry = new PluginRegistry();
        ComplianceRulePipeline pipeline = new ComplianceRulePipeline();

        ModuleContext moduleCtx = new ModuleContext() {
            @Override
            public <C extends Command, R> void registerCommand(
                    Class<C> commandType, CommandHandler<C, R> handler) {
                commandBus.registerHandler(commandType, handler);
            }

            @Override
            public <Q extends Query<R>, R> void registerQuery(
                    Class<Q> queryType, QueryHandler<Q, R> handler) {
                queryBus.registerHandler(queryType, handler);
            }

            @Override
            public void registerProjection(Projection<? extends AccountingEvent> projection) {
                projRegistry.register(projection);
            }
        };

        PluginContext pluginCtx = new PluginContext() {
            @Override
            public void registerComplianceRule(ComplianceRule rule) {
                pipeline.addRule(rule);
            }

            @Override
            public void registerProjection(Projection<? extends AccountingEvent> projection) {
                projRegistry.register(projection);
            }
        };

        for (PlatformPlugin plugin : plugins) {
            plugin.initialize(pluginCtx);
            pluginRegistry.register(plugin);
        }

        for (FinancialModule module : modules) {
            module.register(moduleCtx);
        }

        return new AccountingEngine(
                commandBus,
                queryBus,
                outboxRepository,
                projRegistry,
                pluginRegistry,
                modules);
    }

    private static final class InMemoryOutboxRepositoryLite implements OutboxRepository {
        private final Map<UUID, OutboxEvent> store = new ConcurrentHashMap<>();

        @Override
        public Uni<Void> save(OutboxEvent event) {
            store.put(event.id(), event);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<List<OutboxEvent>> findUnprocessed(int limit) {
            List<OutboxEvent> unprocessed = store.values().stream()
                    .filter(e -> !e.isProcessed())
                    .limit(limit)
                    .toList();
            return Uni.createFrom().item(unprocessed);
        }

        @Override
        public Uni<Void> markProcessed(UUID id) {
            OutboxEvent event = store.get(id);
            if (event != null) {
                store.put(id, event.markProcessed());
            }
            return Uni.createFrom().voidItem();
        }
    }
}
