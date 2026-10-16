package tech.kayys.numou.foundation.application.runtime;

import tech.kayys.numou.foundation.application.command.CommandBus;
import tech.kayys.numou.foundation.application.command.DefaultCommandBus;
import tech.kayys.numou.foundation.application.config.*;
import tech.kayys.numou.foundation.application.query.DefaultQueryBus;
import tech.kayys.numou.foundation.application.query.QueryBus;
import tech.kayys.numou.foundation.application.startup.DefaultStartupValidator;
import tech.kayys.numou.foundation.application.startup.StartupCheck;
import tech.kayys.numou.foundation.application.startup.StartupValidator;

import java.time.Instant;
import java.util.*;

/**
 * Fluent builder for assembling and composing an ApplicationRuntime (config01.md §P4-01 #6).
 */
public final class ApplicationRuntimeBuilder {

    private Configuration configuration;
    private CommandBus commandBus;
    private QueryBus queryBus;
    private final List<ApplicationModule> modules = new ArrayList<>();
    private final List<RuntimeComponent> components = new ArrayList<>();
    private final List<ManagedWorker> workers = new ArrayList<>();
    private final List<StartupCheck> startupChecks = new ArrayList<>();

    public static ApplicationRuntimeBuilder create() {
        return new ApplicationRuntimeBuilder();
    }

    public ApplicationRuntimeBuilder configuration(Configuration configuration) {
        this.configuration = configuration;
        return this;
    }

    public ApplicationRuntimeBuilder commandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
        return this;
    }

    public ApplicationRuntimeBuilder queryBus(QueryBus queryBus) {
        this.queryBus = queryBus;
        return this;
    }

    public ApplicationRuntimeBuilder module(ApplicationModule module) {
        if (module != null) {
            modules.add(module);
        }
        return this;
    }

    public ApplicationRuntimeBuilder component(RuntimeComponent component) {
        if (component != null) {
            components.add(component);
        }
        return this;
    }

    public ApplicationRuntimeBuilder worker(ManagedWorker worker) {
        if (worker != null) {
            workers.add(worker);
        }
        return this;
    }

    public ApplicationRuntimeBuilder startupCheck(StartupCheck check) {
        if (check != null) {
            startupChecks.add(check);
        }
        return this;
    }

    public ApplicationRuntime build() {
        Configuration finalConfig = configuration != null
                ? configuration
                : DefaultConfiguration.builder().build();

        CommandBus finalCommandBus = commandBus != null
                ? commandBus
                : DefaultCommandBus.builder().build();

        QueryBus finalQueryBus = queryBus != null
                ? queryBus
                : DefaultQueryBus.builder().build();

        RuntimeSettings settings = new DefaultRuntimeSettings(finalConfig);
        RuntimeDiagnostics diagnostics = new DefaultRuntimeDiagnostics(Instant.now());
        RuntimeHealth health = new DefaultRuntimeHealth(() -> true);

        RuntimeRegistry registry = new DefaultRuntimeRegistry(
                finalCommandBus,
                finalQueryBus,
                finalConfig,
                settings,
                diagnostics,
                health
        );

        ModuleRegistry modRegistry = new DefaultModuleRegistry();
        for (ApplicationModule m : modules) {
            modRegistry.register(m);
        }

        ApplicationComponentRegistry compRegistry = new DefaultApplicationComponentRegistry();
        for (RuntimeComponent c : components) {
            compRegistry.register(c);
        }

        StartupValidator validator = new DefaultStartupValidator(startupChecks);

        return new DefaultApplicationRuntime(
                registry,
                modRegistry,
                compRegistry,
                validator,
                workers
        );
    }
}
