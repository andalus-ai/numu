package tech.kayys.numou.plugin.application.manifest;

import java.util.List;
import java.util.Set;

public record ApplicationManifest(String id, String version, String publisher, String displayName,
        List<CapabilityRequirement> requiredCapabilities, List<CapabilityRequirement> optionalCapabilities,
        Set<String> permissions, List<NavigationContribution> navigation) {
    public ApplicationManifest {
        requiredCapabilities = List.copyOf(requiredCapabilities);
        optionalCapabilities = List.copyOf(optionalCapabilities);
        permissions = Set.copyOf(permissions);
        navigation = List.copyOf(navigation);
    }
}