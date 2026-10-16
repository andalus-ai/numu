package tech.kayys.numou.foundation.application.tracing;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandContext;
import tech.kayys.numou.foundation.application.command.CommandInvocation;
import tech.kayys.numou.foundation.application.command.CommandMiddleware;
import tech.kayys.numou.foundation.application.middleware.OrderedMiddleware;

import java.util.Objects;

/**
 * Middleware that wraps command execution in a distributed trace span (enhance05.md §8).
 */
public final class CommandTracingMiddleware implements CommandMiddleware, OrderedMiddleware {

    private final Tracing tracing;

    public CommandTracingMiddleware(Tracing tracing) {
        this.tracing = Objects.requireNonNull(tracing, "tracing cannot be null");
    }

    @Override
    public int order() {
        return TRACING_ORDER;
    }

    @Override
    public <R> Uni<R> invoke(CommandContext context, CommandInvocation<R> next) {
        String commandName = context != null && context.command() != null
                ? context.command().getClass().getSimpleName()
                : "UnknownCommand";

        var span = tracing.startSpan(
                "command." + commandName,
                SpanKind.INTERNAL,
                context != null ? context.executionContext() : null
        );

        return next.proceed()
                .invoke(res -> span.success())
                .onFailure()
                .invoke(span::failure)
                .eventually(span::close);
    }
}
