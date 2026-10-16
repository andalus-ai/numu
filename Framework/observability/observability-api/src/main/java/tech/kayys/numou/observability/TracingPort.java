package tech.kayys.syirkah.observability;

import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Starts a span around reactive work. Unlike metrics, span attributes may carry
 * high-cardinality ids (tenant, reservation id) - that is what traces are for.
 *
 * <p>Implementations must end the span on success <em>and</em> failure, mark the
 * span as errored on failure, and re-throw the original failure unchanged.
 */
public interface TracingPort {

    <T> Uni<T> inSpan(String spanName, Map<String, String> attributes, Supplier<Uni<T>> work);
}
