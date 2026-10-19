package tech.kayys.numou.foundation.application.config.profile;

import java.util.List;
import java.util.Map;

/**
 * Resolved profile outcome with inheritance chain and flattened properties (config01.md §P4-03 #14).
 */
public record ResolvedProfile(
        ConfigurationProfileId activeProfile,
        List<ConfigurationProfileId> inheritanceChain,
        Map<String, String> properties
) {
    public ResolvedProfile {
        inheritanceChain = List.copyOf(inheritanceChain);
        properties = Map.copyOf(properties);
    }
}
