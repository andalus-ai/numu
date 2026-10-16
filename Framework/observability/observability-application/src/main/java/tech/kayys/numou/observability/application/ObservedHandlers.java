package tech.kayys.numou.observability.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.query.Query;
import tech.kayys.numou.foundation.application.query.QueryHandler;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.observability.SafeTags;
import tech.kayys.numou.observability.TracingPort;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Decorators that give any {@link CommandHandler}/{@link QueryHandler} a span, a
 * duration timer and an outcome counter with no change to the handler itself.
 *
 * <p>Metric names: {@code handler.duration} and {@code handler.invocations}, tagged
 * with {@code handler} (simple class name of the command/query), {@code type}
 * (command|query) and {@code outcome} (success|failure).
 */
public final class ObservedHandlers {

    private static final MetricName DURATION = MetricName.of("handler.duration");
    private static final MetricName INVOCATIONS = MetricName.of("handler.invocations");

    private ObservedHandlers() { }

    public static <C extends Command, R> CommandHandler<C, R> command(
            String handlerName, CommandHandler<C, R> delegate, MetricsPort metrics, TracingPort tracing) {
        return command(handlerName, delegate, metrics, tracing, System::nanoTime);
    }

    public static <C extends Command, R> CommandHandler<C, R> command(
            String handlerName, CommandHandler<C, R> delegate, MetricsPort metrics, TracingPort tracing, LongSupplier nanoTime) {
        Objects.requireNonNull(delegate, "delegate cannot be null");
        return cmd -> observe("command", handlerName, metrics, tracing, nanoTime, () -> delegate.handle(cmd));
    }

    public static <Q extends Query, R> QueryHandler<Q, R> query(
            String handlerName, QueryHandler<Q, R> delegate, MetricsPort metrics, TracingPort tracing) {
        return query(handlerName, delegate, metrics, tracing, System::nanoTime);
    }

    public static <Q extends Query, R> QueryHandler<Q, R> query(
            String handlerName, QueryHandler<Q, R> delegate, MetricsPort metrics, TracingPort tracing, LongSupplier nanoTime) {
        Objects.requireNonNull(delegate, "delegate cannot be null");
        return q -> observe("query", handlerName, metrics, tracing, nanoTime, () -> delegate.handle(q));
    }

    /** Instruments an arbitrary use case (not a Command/Query handler): span + {@code handler.duration/invocations}. */
    public static <R> Uni<R> useCase(String name, MetricsPort metrics, TracingPort tracing, java.util.function.Supplier<Uni<R>> work) {
        return observe("usecase", name, metrics, tracing, System::nanoTime, work);
    }

    private static <R> Uni<R> observe(String type, String handler, MetricsPort metrics, TracingPort tracing,
                                      LongSupplier nanoTime, java.util.function.Supplier<Uni<R>> work) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(tracing, "tracing cannot be null");
        final long start = nanoTime.getAsLong();
        return tracing.inSpan(type + "." + handler, Map.of("handler", handler, "type", type), () ->
                Uni.createFrom().<R>deferred(() -> work.get())
                        .map(result -> { done(type, handler, "success", metrics, start, nanoTime); return result; })
                        .onFailure().recoverWithUni(t -> {
                            done(type, handler, "failure", metrics, start, nanoTime);
                            return Uni.<R>createFrom().failure(t);
                        }));
    }

    private static void done(String type, String handler, String outcome, MetricsPort metrics, long start, LongSupplier nanoTime) {
        final var tags = SafeTags.of("handler", handler, "type", type, "outcome", outcome);
        metrics.record(SafeTags.apply(DURATION, tags), Duration.ofNanos(nanoTime.getAsLong() - start));
        metrics.increment(SafeTags.apply(INVOCATIONS, tags));
    }
}
