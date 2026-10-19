package tech.kayys.numou.foundation.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.context.CurrentExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Standard implementation of {@link CommandBus} with middleware pipeline orchestration.
 */
public final class DefaultCommandBus implements CommandBus {

    private final CommandHandlerRegistry registry;
    private final List<CommandMiddleware> middlewares;

    public DefaultCommandBus(CommandHandlerRegistry registry) {
        this(registry, List.of());
    }

    public DefaultCommandBus(CommandHandlerRegistry registry, List<CommandMiddleware> middlewares) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.middlewares = middlewares != null ? List.copyOf(middlewares) : List.of();
    }

    @Override
    public <C extends Command, R> Uni<R> dispatch(C command) {
        Objects.requireNonNull(command, "command cannot be null");
        var executionContext = CurrentExecutionContext.getOrEmpty();
        var context = CommandContext.of(executionContext, command);
        return dispatch(command, context);
    }

    @Override
    public <C extends Command, R> Uni<R> dispatch(C command, CommandContext context) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        @SuppressWarnings("unchecked")
        Class<C> commandClass = (Class<C>) command.getClass();
        CommandHandler<C, R> handler = registry.resolve(commandClass);

        CommandInvocation<R> terminalInvocation = () -> handler.handle(command);

        // Build pipeline from end to beginning
        CommandInvocation<R> chain = terminalInvocation;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            CommandMiddleware middleware = middlewares.get(i);
            CommandInvocation<R> next = chain;
            chain = () -> middleware.invoke(context, next);
        }

        return chain.proceed();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CommandHandlerRegistry registry = new CommandHandlerRegistry();
        private final List<CommandMiddleware> middlewares = new ArrayList<>();

        public Builder registry(CommandHandlerRegistry registry) {
            this.registry = registry;
            return this;
        }

        public <C extends Command, R> Builder register(Class<C> commandClass, CommandHandler<C, R> handler) {
            this.registry.register(commandClass, handler);
            return this;
        }

        public Builder addMiddleware(CommandMiddleware middleware) {
            if (middleware != null) {
                this.middlewares.add(middleware);
            }
            return this;
        }

        public DefaultCommandBus build() {
            return new DefaultCommandBus(registry, middlewares);
        }
    }
}
