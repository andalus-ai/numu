package tech.kayys.numou.foundation.application.config.profile;

import java.util.*;

/**
 * Default cycle-detecting implementation of ProfileResolver.
 */
public final class DefaultProfileResolver implements ProfileResolver {

    private final ProfileConfiguration registry;

    public DefaultProfileResolver(ProfileConfiguration registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    @Override
    public ResolvedProfile resolve(ConfigurationProfileId profileId) {
        Objects.requireNonNull(profileId, "profileId cannot be null");

        List<ConfigurationProfileId> chain = new ArrayList<>();
        Set<ConfigurationProfileId> visiting = new HashSet<>();
        ConfigurationProfileId current = profileId;

        while (current != null) {
            if (visiting.contains(current)) {
                throw new ProfileValidationException("Circular profile inheritance detected for profile: " + current.value());
            }
            visiting.add(current);
            chain.add(current);

            ConfigurationProfile prof = registry.require(current);
            current = prof.parent().orElse(null);
        }

        // Always ensure 'default' is at root of chain if not explicitly in it
        if (!chain.contains(ConfigurationProfileId.DEFAULT)) {
            // Only add if default exists in registry
            if (registry.find(ConfigurationProfileId.DEFAULT).isPresent()) {
                chain.add(ConfigurationProfileId.DEFAULT);
            }
        }

        // Reverse so that ancestor comes first, child overrides ancestor
        Collections.reverse(chain);

        Map<String, String> merged = new LinkedHashMap<>();
        for (ConfigurationProfileId id : chain) {
            ConfigurationProfile p = registry.require(id);
            merged.putAll(p.properties());
        }

        return new ResolvedProfile(profileId, chain, merged);
    }
}
