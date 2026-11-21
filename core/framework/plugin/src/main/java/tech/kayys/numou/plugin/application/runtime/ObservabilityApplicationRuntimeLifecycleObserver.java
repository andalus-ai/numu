package tech.kayys.numou.plugin.application.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.observability.SafeTags;
import tech.kayys.numou.observability.TracingPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges application runtime lifecycle events to Numou's shared observability ports.
 *
 * <p>Metrics deliberately use only bounded event-type/outcome tags. Application, provider and
 * plugin identifiers are attached to traces instead, avoiding unbounded metric cardinality.
 * Tracing is submitted through the reactive tracing port without blocking plugin lifecycle calls.
 */
public final class ObservabilityApplicationRuntimeLifecycleObserver
        implements ApplicationRuntimeLifecycleObserver {

    private static final MetricName EVENTS = MetricName.of("application.runtime.lifecycle.events");
    private static final MetricName FAILURES = MetricName.of("application.runtime.lifecycle.failures");
    private static final String SPAN_NAME = "application.runtime.lifecycle";

    private final MetricsPort metrics;
    private final TracingPort tracing;

    public ObservabilityApplicationRuntimeLifecycleObserver(MetricsPort metrics, TracingPort tracing) {
        this.metrics = Objects.requireNonNull(metrics, "Metrics port is required");
        this.tracing = Objects.requireNonNull(tracing, "Tracing port is required");
    }

    @Override
    public void onEvent(ApplicationRuntimeLifecycleEvent event) {
        Objects.requireNonNull(event, "Lifecycle event is required");
        String eventType = event.type().name().toLowerCase(java.util.Locale.ROOT);
        Map<String, String> metricTags = Map.of("type", eventType);
        try {
            metrics.increment(SafeTags.apply(EVENTS, metricTags));
            if (isFailure(event.type())) {
                metrics.increment(SafeTags.apply(FAILURES, metricTags));
            }
        } catch (RuntimeException ignored) {
            // Metrics are operational hints and must never interfere with runtime transitions.
        }

        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("application.id", event.applicationId());
            attributes.put("lifecycle.event", eventType);
            if (event.providerId() != null) attributes.put("provider.id", event.providerId());
            if (event.pluginId() != null) attributes.put("plugin.id", event.pluginId());
            tracing.inSpan(SPAN_NAME, Map.copyOf(attributes),
                    () -> Uni.createFrom().item(Boolean.TRUE))
                    .subscribe().with(ignored -> { }, ignored -> { });
        } catch (RuntimeException ignored) {
            // A tracing exporter or subscription failure must not affect plugin lifecycle work.
        }
    }

    private static boolean isFailure(ApplicationRuntimeLifecycleEvent.Type type) {
        return switch (type) {
            case STARTUP_FAILED, PROVIDER_ROLLBACK_FAILED, ROLLBACK_FAILED,
                    PROVIDER_STOP_FAILED, SHUTDOWN_FAILED -> true;
            default -> false;
        };
    }
}
