package tech.kayys.numou.plugin.application.resolution;

import java.util.List;
import java.util.Map;

public record CapabilityResolution(Map<String, CapabilityProvider> providersByCapability,
        List<CapabilityProvider> initializationOrder, List<String> unavailableOptionalCapabilities) {
    public CapabilityResolution {
        providersByCapability = Map.copyOf(providersByCapability);
        initializationOrder = List.copyOf(initializationOrder);
        unavailableOptionalCapabilities = List.copyOf(unavailableOptionalCapabilities);
    }
}