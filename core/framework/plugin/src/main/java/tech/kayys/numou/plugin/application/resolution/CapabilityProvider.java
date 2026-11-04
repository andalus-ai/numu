package tech.kayys.numou.plugin.application.resolution;

import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import java.util.List;

public record CapabilityProvider(String providerId, String capabilityId, String contractVersion,
                                 List<CapabilityRequirement> dependencies) {
    public CapabilityProvider {
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("Provider ID is required");
        if (capabilityId == null || capabilityId.isBlank()) throw new IllegalArgumentException("Capability ID is required");
        if (contractVersion == null || contractVersion.isBlank()) throw new IllegalArgumentException("Contract version is required");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}