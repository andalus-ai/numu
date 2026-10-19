package tech.kayys.numou.foundation.application.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of RuntimeDiagnostics.
 */
public final class DefaultRuntimeDiagnostics implements RuntimeDiagnostics {

    private final Instant startedAt;
    private final Map<String, Object> additionalData = new LinkedHashMap<>();

    public DefaultRuntimeDiagnostics(Instant startedAt) {
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt cannot be null");
    }

    public synchronized void record(String key, Object value) {
        if (key != null && value != null) {
            additionalData.put(key, value);
        }
    }

    @Override
    public Instant startedAt() {
        return startedAt;
    }

    @Override
    public long uptimeMillis() {
        return Math.max(0, System.currentTimeMillis() - startedAt.toEpochMilli());
    }

    @Override
    public synchronized Map<String, Object> diagnosticData() {
        Map<String, Object> data = new LinkedHashMap<>(additionalData);
        data.put("uptimeMs", uptimeMillis());
        data.put("startedAt", startedAt.toString());
        return Collections.unmodifiableMap(data);
    }
}
