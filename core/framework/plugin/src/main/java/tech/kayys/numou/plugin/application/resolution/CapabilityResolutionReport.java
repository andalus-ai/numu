package tech.kayys.numou.plugin.application.resolution;

import java.util.List;

/** Immutable, deployment-neutral explanation of a successful capability composition. */
public record CapabilityResolutionReport(
        List<CapabilityProvider> selectedProviders,
        List<CapabilityResolutionEdge> dependencyEdges,
        List<String> unavailableOptionalCapabilities,
        List<String> diagnostics) {
    public CapabilityResolutionReport {
        selectedProviders = List.copyOf(selectedProviders);
        dependencyEdges = List.copyOf(dependencyEdges);
        unavailableOptionalCapabilities = List.copyOf(unavailableOptionalCapabilities);
        diagnostics = List.copyOf(diagnostics);
    }
}
