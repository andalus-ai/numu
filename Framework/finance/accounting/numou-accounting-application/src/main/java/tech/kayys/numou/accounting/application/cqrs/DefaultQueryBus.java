package tech.kayys.syirkah.accounting.application.cqrs;

import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultQueryBus implements QueryBus {
    private final Map<Class<?>, QueryHandler<?, ?>> handlers = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <Q extends Query<R>, R> Uni<R> execute(Q query) {
        QueryHandler<Q, R> handler = (QueryHandler<Q, R>) handlers.get(query.getClass());
        if (handler == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("No handler registered for query: " + query.getClass().getName()));
        }
        return handler.handle(query);
    }

    @Override
    public <Q extends Query<R>, R> void registerHandler(Class<Q> queryClass, QueryHandler<Q, R> handler) {
        handlers.put(queryClass, handler);
    }
}
