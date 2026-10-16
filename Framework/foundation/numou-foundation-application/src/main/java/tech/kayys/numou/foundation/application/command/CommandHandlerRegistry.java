package tech.kayys.numou.foundation.application.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding mappings from concrete {@link Command} types to their {@link CommandHandler}.
 */
public final class CommandHandlerRegistry {

    private final Map<Class<? extends Command>, CommandHandler<?, ?>> handlers = new ConcurrentHashMap<>();

    public <C extends Command, R> CommandHandlerRegistry register(Class<C> commandType, CommandHandler<C, R> handler) {
        Objects.requireNonNull(commandType, "commandType cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        handlers.put(commandType, handler);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <C extends Command, R> Optional<CommandHandler<C, R>> find(Class<C> commandType) {
        Objects.requireNonNull(commandType, "commandType cannot be null");
        return Optional.ofNullable((CommandHandler<C, R>) handlers.get(commandType));
    }

    public <C extends Command, R> CommandHandler<C, R> resolve(Class<C> commandType) {
        return this.<C, R>find(commandType).orElseThrow(() ->
                new IllegalStateException("No CommandHandler registered for command type: " + commandType.getName())
        );
    }

    public boolean contains(Class<? extends Command> commandType) {
        return handlers.containsKey(commandType);
    }
}
