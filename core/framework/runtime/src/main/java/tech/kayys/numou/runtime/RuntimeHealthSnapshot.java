package tech.kayys.numou.runtime;

import java.time.Instant;
import java.util.List;

/** Immutable liveness/readiness information suitable for health adapters. */
public record RuntimeHealthSnapshot(
        String applicationId,
        String profile,
        RuntimeReadiness readiness,
        boolean live,
        boolean ready,
        List<String> activeProviderIds,
        List<String> unavailableOptionalCapabilities,
        Instant observedAt,
        String failure) {

    public RuntimeHealthSnapshot {
        activeProviderIds = List.copyOf(activeProviderIds);
        unavailableOptionalCapabilities = List.copyOf(unavailableOptionalCapabilities);
    }
}
