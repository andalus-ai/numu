package tech.kayys.numou.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Allow-list of low-cardinality metric dimensions. Anything else is dropped
 * silently rather than failing a request because of a metrics label.
 */
public final class SafeTags {

    public static final Set<String> ALLOWED = Set.of(
            "handler", "outcome", "context", "type", "station", "channel", "status", "cache", "reason", "operation");

    private SafeTags() { }

    public static MetricName apply(MetricName base, Map<String, String> candidate) {
        MetricName result = base;
        for (final var e : candidate.entrySet()) {
            if (ALLOWED.contains(e.getKey()) && e.getValue() != null && !e.getValue().isBlank()) {
                result = result.with(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    public static Map<String, String> of(String... kv) {
        final var m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
