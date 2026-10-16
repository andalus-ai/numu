package tech.kayys.numou.foundation.application.config.profile;

import java.util.*;

/**
 * Default implementation of ProfileConfiguration.
 */
public final class DefaultProfileConfiguration implements ProfileConfiguration {

    private final Map<ConfigurationProfileId, ConfigurationProfile> profiles = new HashMap<>();

    public DefaultProfileConfiguration() {
        // Ensure default profile always exists
        register(ConfigurationProfile.builder(ConfigurationProfileId.DEFAULT).build());
    }

    @Override
    public synchronized void register(ConfigurationProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        profiles.put(profile.id(), profile);
    }

    @Override
    public synchronized Optional<ConfigurationProfile> find(ConfigurationProfileId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(id));
    }

    @Override
    public synchronized Set<ConfigurationProfileId> profiles() {
        return Set.copyOf(profiles.keySet());
    }
}
