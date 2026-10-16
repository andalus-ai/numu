package tech.kayys.numou.foundation.application.config.profile;

import java.util.Map;
import java.util.Optional;

/**
 * Profile contract contributing configuration properties (config01.md §P4-03 #4).
 */
public interface ConfigurationProfile {

    ConfigurationProfileId id();

    Optional<ConfigurationProfileId> parent();

    Map<String, String> properties();

    static Builder builder(ConfigurationProfileId id) {
        return new Builder(id);
    }

    static Builder builder(String id) {
        return new Builder(ConfigurationProfileId.of(id));
    }

    class Builder {
        private final ConfigurationProfileId id;
        private ConfigurationProfileId parent;
        private final java.util.Map<String, String> properties = new java.util.LinkedHashMap<>();

        public Builder(ConfigurationProfileId id) {
            this.id = java.util.Objects.requireNonNull(id, "id cannot be null");
        }

        public Builder parent(ConfigurationProfileId parent) {
            this.parent = parent;
            return this;
        }

        public Builder parent(String parent) {
            this.parent = parent != null ? ConfigurationProfileId.of(parent) : null;
            return this;
        }

        public Builder property(String key, String value) {
            java.util.Objects.requireNonNull(key, "key cannot be null");
            if (value != null) {
                properties.put(key, value);
            }
            return this;
        }

        public Builder properties(Map<String, String> props) {
            if (props != null) {
                properties.putAll(props);
            }
            return this;
        }

        public ConfigurationProfile build() {
            return new SimpleConfigurationProfile(id, Optional.ofNullable(parent), Map.copyOf(properties));
        }
    }
}
