package tech.kayys.numou.foundation.application.middleware;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.query.QueryContext;
import tech.kayys.numou.foundation.application.query.QueryInvocation;
import tech.kayys.numou.foundation.application.query.QueryMiddleware;

import java.util.function.Function;

/**
 * Middleware that handles and classifies exceptions thrown during query execution (enhance03.md §18).
 */
public final class ErrorHandlingQueryMiddleware implements QueryMiddleware, OrderedMiddleware {

    private final Function<Throwable, Throwable> exceptionClassifier;

    public ErrorHandlingQueryMiddleware() {
        this(Function.identity());
    }

    public ErrorHandlingQueryMiddleware(Function<Throwable, Throwable> exceptionClassifier) {
        this.exceptionClassifier = exceptionClassifier != null ? exceptionClassifier : Function.identity();
    }

    @Override
    public int order() {
        return ERROR_ORDER;
    }

    @Override
    public <R> Uni<R> invoke(QueryContext context, QueryInvocation<R> next) {
        return next.proceed()
                .onFailure()
                .transform(exceptionClassifier);
    }
}
