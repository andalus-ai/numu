package tech.kayys.syirkah.cache.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.cache.Cache;
import tech.kayys.syirkah.cache.CacheKey;
import tech.kayys.syirkah.foundation.application.query.Query;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;

import java.util.Objects;
import java.util.function.Function;

/**
 * Decorator that serves a {@link QueryHandler} through a {@link Cache}.
 *
 * <p>Only queries are cacheable - commands never go through this. The key
 * function is required to include the tenant (see {@link CacheKey#of}).
 */
public final class CachedQueryHandler<Q extends Query, R> implements QueryHandler<Q, R> {

    private final QueryHandler<Q, R> delegate;
    private final Cache<R> cache;
    private final Function<Q, CacheKey> keyFunction;

    public CachedQueryHandler(QueryHandler<Q, R> delegate, Cache<R> cache, Function<Q, CacheKey> keyFunction) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.keyFunction = Objects.requireNonNull(keyFunction, "keyFunction cannot be null");
    }

    @Override
    public Uni<R> handle(Q query) {
        return cache.getOrLoad(keyFunction.apply(query), () -> delegate.handle(query));
    }
}
