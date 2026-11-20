package tech.kayys.numou.plugin.application.runtime;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.observability.TracingPort;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ObservabilityApplicationRuntimeLifecycleObserverTest {

    private static final class RecordingMetrics implements MetricsPort {
        private final List<MetricName> increments = new ArrayList<>();

        @Override public void increment(MetricName name, long amount) {
            for (long i = 0; i < amount; i++) increments.add(name);
        }
        @Override public void record(MetricName name, Duration duration) { }
        @Override public void gauge(MetricName name, Supplier<Number> value) { }
    }

    private static final class RecordingTracing implements TracingPort {
        private final List<String> spanNames = new ArrayList<>();
        private final List<Map<String, String>> attributes = new ArrayList<>();

        @Override
        public <T> Uni<T> inSpan(String spanName, Map<String, String> spanAttributes, Supplier<Uni<T>> work) {
            return Uni.createFrom().deferred(() -> {
                spanNames.add(spanName);
                attributes.add(Map.copyOf(spanAttributes));
                return work.get();
            });
        }
    }

    @Test
    void recordsBoundedMetricsAndDetailedTraceAttributes() {
        RecordingMetrics metrics = new RecordingMetrics();
        RecordingTracing tracing = new RecordingTracing();
        var observer = new ObservabilityApplicationRuntimeLifecycleObserver(metrics, tracing);

        observer.onEvent(new ApplicationRuntimeLifecycleEvent("erp-tenant-42", "provider-party",
                "plugin-party", ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STARTED,
                Instant.parse("2026-11-20T03:00:00Z"), null));
        observer.onEvent(new ApplicationRuntimeLifecycleEvent("erp-tenant-42", "provider-party",
                "plugin-party", ApplicationRuntimeLifecycleEvent.Type.PROVIDER_START_STARTED,
                Instant.parse("2026-11-20T03:00:01Z"), null));
        observer.onEvent(new ApplicationRuntimeLifecycleEvent("erp-tenant-42", "provider-party",
                "plugin-party", ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOP_FAILED,
                Instant.parse("2026-11-20T03:00:02Z"), "IllegalStateException"));

        assertThat(metrics.increments).hasSize(4);
        assertThat(metrics.increments).extracting(MetricName::name)
                .containsExactly(
                        "application.runtime.lifecycle.events",
                        "application.runtime.lifecycle.events",
                        "application.runtime.lifecycle.events",
                        "application.runtime.lifecycle.failures");
        assertThat(metrics.increments).allSatisfy(metric -> {
            assertThat(metric.tags()).containsOnlyKeys("type");
            assertThat(metric.tags()).doesNotContainKeys("application.id", "provider.id", "plugin.id");
        });
        assertThat(metrics.increments.get(3).tags()).containsEntry("type", "provider_stop_failed");

        assertThat(tracing.spanNames).containsExactly(
                "application.runtime.lifecycle",
                "application.runtime.lifecycle",
                "application.runtime.lifecycle");
        assertThat(tracing.attributes).allSatisfy(attributes -> {
            assertThat(attributes).containsEntry("application.id", "erp-tenant-42")
                    .containsEntry("provider.id", "provider-party")
                    .containsEntry("plugin.id", "plugin-party");
        });
        assertThat(tracing.attributes.get(2)).containsEntry("lifecycle.event", "provider_stop_failed");
    }

    @Test
    void metricsFailureDoesNotPreventTracingAndTracingFailureDoesNotEscape() {
        TracingPort recordingTracing = new RecordingTracing();
        MetricsPort failingMetrics = new MetricsPort() {
            @Override public void increment(MetricName name, long amount) {
                throw new IllegalStateException("metrics unavailable");
            }
            @Override public void record(MetricName name, Duration duration) { }
            @Override public void gauge(MetricName name, Supplier<Number> value) { }
        };
        var metricsFailureObserver = new ObservabilityApplicationRuntimeLifecycleObserver(
                failingMetrics, recordingTracing);
        assertThatCode(() -> metricsFailureObserver.onEvent(event())).doesNotThrowAnyException();

        MetricsPort recordingMetrics = new RecordingMetrics();
        TracingPort failingTracing = new TracingPort() {
            @Override public <T> Uni<T> inSpan(String spanName, Map<String, String> attributes,
                    Supplier<Uni<T>> work) {
                throw new IllegalStateException("tracing unavailable");
            }
        };
        var tracingFailureObserver = new ObservabilityApplicationRuntimeLifecycleObserver(
                recordingMetrics, failingTracing);
        assertThatCode(() -> tracingFailureObserver.onEvent(event())).doesNotThrowAnyException();
        assertThat(recordingMetrics.increments).hasSize(1);
    }

    private ApplicationRuntimeLifecycleEvent event() {
        return new ApplicationRuntimeLifecycleEvent("application-one", null, null,
                ApplicationRuntimeLifecycleEvent.Type.APPLICATION_STARTING,
                Instant.parse("2026-11-20T03:00:00Z"), null);
    }
}
