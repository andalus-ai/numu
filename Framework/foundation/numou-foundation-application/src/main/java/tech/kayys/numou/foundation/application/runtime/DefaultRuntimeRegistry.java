package tech.kayys.numou.foundation.application.runtime;

import tech.kayys.numou.foundation.application.command.CommandBus;
import tech.kayys.numou.foundation.application.config.Configuration;
import tech.kayys.numou.foundation.application.config.RuntimeSettings;
import tech.kayys.numou.foundation.application.query.QueryBus;

import java.util.Objects;

/**
 * Default implementation of RuntimeRegistry.
 */
public final class DefaultRuntimeRegistry implements RuntimeRegistry {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final Configuration configuration;
    private final RuntimeSettings settings;
    private final RuntimeDiagnostics diagnostics;
    private final RuntimeHealth health;

    public DefaultRuntimeRegistry(
            CommandBus commandBus,
            QueryBus queryBus,
            Configuration configuration,
            RuntimeSettings settings,
            RuntimeDiagnostics diagnostics,
            RuntimeHealth health) {

        this.commandBus = Objects.requireNonNull(commandBus, "commandBus cannot be null");
        this.queryBus = Objects.requireNonNull(queryBus, "queryBus cannot be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration cannot be null");
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics cannot be null");
        this.health = Objects.requireNonNull(health, "health cannot be null");
    }

    @Override
    public CommandBus commandBus() {
        return commandBus;
    }

    @Override
    public QueryBus queryBus() {
        return queryBus;
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public RuntimeSettings settings() {
        return settings;
    }

    @Override
    public RuntimeDiagnostics diagnostics() {
        return diagnostics;
    }

    @Override
    public RuntimeHealth health() {
        return health;
    }
}
