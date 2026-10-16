package tech.kayys.numou.foundation.application.middleware;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandContext;
import tech.kayys.numou.foundation.application.command.CommandInvocation;
import tech.kayys.numou.foundation.application.command.CommandMiddleware;
import tech.kayys.numou.foundation.application.query.QueryContext;
import tech.kayys.numou.foundation.application.query.QueryInvocation;
import tech.kayys.numou.foundation.application.query.QueryMiddleware;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;

/**
 * Middleware that logs dispatching and completion metrics for commands and queries.
 */
public final class LoggingMiddleware implements CommandMiddleware, QueryMiddleware, OrderedMiddleware {

    private static final System.Logger LOGGER = System.getLogger(LoggingMiddleware.class.getName());

    @Override
    public int order() {
        return METRICS_ORDER;
    }

    @Override
    public <R> Uni<R> invoke(CommandContext context, CommandInvocation<R> next) {
        String commandName = context != null && context.command() != null
                ? context.command().getClass().getSimpleName()
                : "UnknownCommand";
        Instant start = Instant.now();

        LOGGER.log(Level.DEBUG, "Dispatching command {0}", commandName);

        return next.proceed()
                .invoke(res -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    LOGGER.log(Level.DEBUG, "Completed command {0} in {1}ms", commandName, elapsed);
                })
                .onFailure()
                .invoke(error -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    LOGGER.log(Level.WARNING, "Failed command {0} in {1}ms: {2}", commandName, elapsed, error.getMessage());
                });
    }

    @Override
    public <R> Uni<R> invoke(QueryContext context, QueryInvocation<R> next) {
        String queryName = context != null && context.query() != null
                ? context.query().getClass().getSimpleName()
                : "UnknownQuery";
        Instant start = Instant.now();

        LOGGER.log(Level.DEBUG, "Dispatching query {0}", queryName);

        return next.proceed()
                .invoke(res -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    LOGGER.log(Level.DEBUG, "Completed query {0} in {1}ms", queryName, elapsed);
                })
                .onFailure()
                .invoke(error -> {
                    long elapsed = Duration.between(start, Instant.now()).toMillis();
                    LOGGER.log(Level.WARNING, "Failed query {0} in {1}ms: {2}", queryName, elapsed, error.getMessage());
                });
    }
}
