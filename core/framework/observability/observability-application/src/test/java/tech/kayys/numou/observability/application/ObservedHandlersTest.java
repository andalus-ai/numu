package tech.kayys.numou.observability.application;

import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.observability.SafeTags;
import tech.kayys.numou.observability.TracingPort;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.command.Command;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservedHandlersTest {

    record Ping(String tenantSecret) implements Command { }

    static final class FakeMetrics implements MetricsPort {
        final List<MetricName> increments = new ArrayList<>();
        final List<MetricName> timers = new ArrayList<>();
        public void increment(MetricName n, long a) { increments.add(n); }
        public void record(MetricName n, Duration d) { timers.add(n); }
        public void gauge(MetricName n, Supplier<Number> v) { }
    }

    static final class FakeTracing implements TracingPort {
        final List<String> spans = new ArrayList<>();
        public <T> Uni<T> inSpan(String name, Map<String, String> attrs, Supplier<Uni<T>> work) {
            spans.add(name);
            return work.get();
        }
    }

    @Test
    void successRecordsTimerCounterAndSpan() {
        final var m = new FakeMetrics(); final var t = new FakeTracing();
        final var h = ObservedHandlers.<Ping, String>command("Ping", c -> Uni.createFrom().item("pong"), m, t);
        assertThat(h.handle(new Ping("x")).await().indefinitely()).isEqualTo("pong");
        assertThat(t.spans).containsExactly("command.Ping");
        assertThat(m.timers).hasSize(1);
        assertThat(m.increments.get(0).tags()).containsEntry("outcome", "success").containsEntry("handler", "Ping");
    }

    @Test
    void failureIsRecordedAndOriginalExceptionPropagates() {
        final var m = new FakeMetrics(); final var t = new FakeTracing();
        final var boom = new IllegalStateException("boom");
        final var h = ObservedHandlers.<Ping, String>command("Ping", c -> Uni.createFrom().failure(boom), m, t);
        assertThatThrownBy(() -> h.handle(new Ping("x")).await().indefinitely()).isSameAs(boom);
        assertThat(m.increments.get(0).tags()).containsEntry("outcome", "failure");
    }

    @Test
    void synchronousThrowFromHandlerIsAlsoObserved() {
        final var m = new FakeMetrics(); final var t = new FakeTracing();
        final var h = ObservedHandlers.<Ping, String>command("Ping", c -> { throw new IllegalArgumentException("bad"); }, m, t);
        assertThatThrownBy(() -> h.handle(new Ping("x")).await().indefinitely()).isInstanceOf(IllegalArgumentException.class);
        assertThat(m.increments.get(0).tags()).containsEntry("outcome", "failure");
    }

    @Test
    void highCardinalityTagsAreDropped() {
        final var base = MetricName.of("handler.invocations");
        final var tagged = SafeTags.apply(base, Map.of("tenant", "t-123", "reservationId", "r-9", "outcome", "success"));
        assertThat(tagged.tags()).containsOnlyKeys("outcome");
    }

    @Test
    void useCasesAreInstrumentedLikeHandlers() {
        final var m = new FakeMetrics(); final var t = new FakeTracing();
        final var out = ObservedHandlers.useCase("reservation.create", m, t, () -> Uni.createFrom().item(42)).await().indefinitely();
        assertThat(out).isEqualTo(42);
        assertThat(t.spans).containsExactly("usecase.reservation.create");
        assertThat(m.increments.get(0).tags()).containsEntry("handler", "reservation.create").containsEntry("type", "usecase").containsEntry("outcome", "success");
    }
}
