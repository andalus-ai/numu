package tech.kayys.numou.foundation.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.context.CurrentExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Standard implementation of {@link QueryBus} with middleware pipeline orchestration.
 */
public final class DefaultQueryBus implements QueryBus {

    private final QueryHandlerRegistry registry;
    private final List<QueryMiddleware> middlewares;

    public DefaultQueryBus(QueryHandlerRegistry registry) {
        this(registry, List.of());
    }

    public DefaultQueryBus(QueryHandlerRegistry registry, List<QueryMiddleware> middlewares) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.middlewares = middlewares != null ? List.copyOf(middlewares) : List.of();
    }

    @Override
    public <Q extends Query, R> Uni<R> dispatch(Q query) {
        Objects.requireNonNull(query, "query cannot be null");
        var executionContext = CurrentExecutionContext.getOrEmpty();
        var context = QueryContext.of(executionContext, query);
        return dispatch(query, context);
    }

    @Override
    public <Q extends Query, R> Uni<R> dispatch(Q query, QueryContext context) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        @SuppressWarnings("unchecked")
        Class<Q> queryClass = (Class<Q>) query.getClass();
        QueryHandler<Q, R> handler = registry.resolve(queryClass);

        QueryInvocation<R> terminalInvocation = () -> handler.handle(query);

        // Build pipeline from end to beginning
        QueryInvocation<R> chain = terminalInvocation;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            QueryMiddleware middleware = middlewares.get(i);
            QueryInvocation<R> next = chain;
            chain = () -> middleware.invoke(context, next);
        }

        return chain.proceed();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private QueryHandlerRegistry registry = new QueryHandlerRegistry();
        private final List<QueryMiddleware> middlewares = new ArrayList<>();

        public Builder registry(QueryHandlerRegistry registry) {
            this.registry = registry;
            return this;
        }

        public <Q extends Query, R> Builder register(Class<Q> queryClass, QueryHandler<Q, R> handler) {
            this.registry.register(queryClass, handler);
            return this;
        }

        public Builder addMiddleware(QueryMiddleware middleware) {
            if (middleware != null) {
                this.middlewares.add(middleware);
            }
            return this;
        }

        public DefaultQueryBus build() {
            return new DefaultQueryBus(registry, middlewares);
        }
    }
}
