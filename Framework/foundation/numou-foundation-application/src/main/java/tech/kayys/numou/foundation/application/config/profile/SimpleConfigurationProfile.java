package tech.kayys.numou.foundation.application.config.profile;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

record SimpleConfigurationProfile(
        ConfigurationProfileId id,
        Optional<ConfigurationProfileId> parent,
        Map<String, String> properties
) implements ConfigurationProfile {

    SimpleConfigurationProfile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(parent, "parent cannot be null");
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }
}
