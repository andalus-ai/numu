package tech.kayys.numou.plugin.application.resolution;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record CapabilityResolution(Map<String, CapabilityProvider> providersByCapability,
        List<CapabilityProvider> initializationOrder, List<String> unavailableOptionalCapabilities,
        CapabilityResolutionReport report) {
    public CapabilityResolution {
        providersByCapability = Collections.unmodifiableMap(new TreeMap<>(providersByCapability));
        initializationOrder = List.copyOf(initializationOrder);
        unavailableOptionalCapabilities = List.copyOf(unavailableOptionalCapabilities);
        if (report == null) {
            throw new IllegalArgumentException("Capability resolution report is required");
        }
    }

    /** Retains the original construction contract for existing callers. */
    public CapabilityResolution(Map<String, CapabilityProvider> providersByCapability,
            List<CapabilityProvider> initializationOrder, List<String> unavailableOptionalCapabilities) {
        this(providersByCapability, initializationOrder, unavailableOptionalCapabilities,
                new CapabilityResolutionReport(
                        new TreeMap<>(providersByCapability).values().stream().toList(),
                        List.of(), unavailableOptionalCapabilities, List.of()));
    }
}
