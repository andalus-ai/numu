package tech.kayys.numou.foundation.application.query;

import tech.kayys.numou.foundation.application.context.ExecutionContext;

import java.time.Instant;
import java.util.*;

/**
 * Execution context metadata for a query invocation.
 */
public final class QueryContext {

    private final ExecutionContext executionContext;
    private final Query query;
    private final Instant createdAt;
    private final Map<String, Object> attributes;

    public QueryContext(ExecutionContext executionContext, Query query) {
        this(executionContext, query, Map.of());
    }

    public QueryContext(ExecutionContext executionContext, Query query, Map<String, Object> attributes) {
        this.executionContext = executionContext != null ? executionContext : ExecutionContext.empty();
        this.query = Objects.requireNonNull(query, "query cannot be null");
        this.createdAt = Instant.now();
        this.attributes = attributes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(attributes)) : Map.of();
    }

    public static QueryContext of(Query query) {
        return new QueryContext(ExecutionContext.empty(), query);
    }

    public static QueryContext of(ExecutionContext executionContext, Query query) {
        return new QueryContext(executionContext, query);
    }

    public ExecutionContext executionContext() {
        return executionContext;
    }

    public Query query() {
        return query;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object attribute(String key) {
        return attributes.get(key);
    }

    public QueryContext withAttribute(String key, Object value) {
        var map = new LinkedHashMap<>(this.attributes);
        map.put(key, value);
        return new QueryContext(this.executionContext, this.query, map);
    }

    public QueryContext withExecutionContext(ExecutionContext context) {
        return new QueryContext(context, this.query, this.attributes);
    }
}
