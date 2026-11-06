package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolutionReport;

import java.util.List;

/**
 * Runtime-facing, immutable composition plan for one application.
 * It describes what to initialize but does not start plugins or choose a deployment topology.
 */
public record ApplicationComposition(
        String applicationId,
        String applicationVersion,
        List<CapabilityProvider> initializationOrder,
        CapabilityResolutionReport resolutionReport) {
    public ApplicationComposition {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("Application ID is required");
        }
        if (applicationVersion == null || applicationVersion.isBlank()) {
            throw new IllegalArgumentException("Application version is required");
        }
        initializationOrder = List.copyOf(initializationOrder);
        if (resolutionReport == null) {
            throw new IllegalArgumentException("Capability resolution report is required");
        }
    }
}
