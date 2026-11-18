package tech.kayys.numou.plugin.application.manifest;

public record CapabilityRequirement(String id, String contractRange) {
    public CapabilityRequirement {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Capability ID is required");
        if (contractRange == null || contractRange.isBlank()) throw new IllegalArgumentException("Capability contract range is required");
        VersionRange.parse(contractRange);
    }
}
