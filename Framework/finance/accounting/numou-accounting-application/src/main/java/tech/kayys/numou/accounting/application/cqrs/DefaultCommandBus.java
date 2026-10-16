package tech.kayys.syirkah.accounting.application.cqrs;

import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultCommandBus implements CommandBus {
    private final Map<Class<?>, CommandHandler<?, ?>> handlers = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Command, R> Uni<R> dispatch(C command) {
        CommandHandler<C, R> handler = (CommandHandler<C, R>) handlers.get(command.getClass());
        if (handler == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("No handler registered for command: " + command.getClass().getName()));
        }
        return handler.handle(command);
    }

    @Override
    public <C extends Command, R> void registerHandler(Class<C> commandClass, CommandHandler<C, R> handler) {
        handlers.put(commandClass, handler);
    }
}
