package tech.kayys.numou.plugin.application.resolution;

/**
 * One declared edge in an application's capability composition graph.
 * A null sourceCapability identifies a direct application-manifest requirement.
 */
public record CapabilityResolutionEdge(
        String sourceCapability,
        String targetCapability,
        String contractRange,
        boolean required,
        ResolutionStatus status,
        String selectedProviderId,
        String selectedContractVersion) {

    public enum ResolutionStatus {
        RESOLVED,
        UNAVAILABLE_OPTIONAL
    }
}
