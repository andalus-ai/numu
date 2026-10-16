package tech.kayys.numou.foundation.application.query;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding associations between {@link Query} types and their corresponding {@link QueryHandler}.
 */
public final class QueryHandlerRegistry {

    private final Map<Class<? extends Query>, QueryHandler<?, ?>> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a handler for the given query class.
     *
     * @param queryClass the class of query
     * @param handler the handler implementation
     * @param <Q> the query type
     * @param <R> the query result type
     */
    public <Q extends Query, R> void register(Class<Q> queryClass, QueryHandler<Q, R> handler) {
        Objects.requireNonNull(queryClass, "queryClass cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        handlers.put(queryClass, handler);
    }

    /**
     * Resolves the handler registered for the specified query class.
     *
     * @param queryClass the query type to look up
     * @param <Q> the query type
     * @param <R> the result type
     * @return the registered query handler
     * @throws IllegalStateException if no handler is registered
     */
    @SuppressWarnings("unchecked")
    public <Q extends Query, R> QueryHandler<Q, R> resolve(Class<Q> queryClass) {
        Objects.requireNonNull(queryClass, "queryClass cannot be null");
        QueryHandler<?, ?> handler = handlers.get(queryClass);
        if (handler == null) {
            throw new IllegalStateException("No query handler registered for " + queryClass.getName());
        }
        return (QueryHandler<Q, R>) handler;
    }

    /**
     * Checks if a handler is registered for the specified query class.
     *
     * @param queryClass the query type
     * @return true if registered, false otherwise
     */
    public boolean hasHandler(Class<? extends Query> queryClass) {
        return handlers.containsKey(queryClass);
    }
}
