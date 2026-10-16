package tech.kayys.numou.foundation.application.config;

import java.time.Duration;
import java.util.*;

/**
 * Standard implementation of {@link Configuration} supporting precedence layering and snapshots.
 */
public final class DefaultConfiguration implements Configuration {

    private final ConfigurationSnapshot snapshot;

    private DefaultConfiguration(ConfigurationSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot cannot be null");
    }

    public static DefaultConfiguration fromSnapshot(ConfigurationSnapshot snapshot) {
        return new DefaultConfiguration(snapshot);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <T> Optional<T> get(ConfigurationKey<T> key) {
        return snapshot.get(key);
    }

    @Override
    public boolean contains(ConfigurationKey<?> key) {
        return snapshot.contains(key);
    }

    @Override
    public Optional<String> get(String key) {
        return snapshot.get(key);
    }

    @Override
    public boolean contains(String key) {
        return snapshot.contains(key);
    }

    @Override
    public ConfigurationSnapshot snapshot() {
        return snapshot;
    }

    public static final class Builder {
        private final List<ConfigurationSource> sources = new ArrayList<>();
        private final Set<ConfigurationKey<?>> registeredKeys = new HashSet<>(RuntimeConfigurationKeys.ALL);
        private final Map<String, String> manualOverrides = new LinkedHashMap<>();

        public Builder addSource(ConfigurationSource source) {
            if (source != null) {
                sources.add(source);
            }
            return this;
        }

        public Builder addSources(Collection<ConfigurationSource> sources) {
            if (sources != null) {
                for (ConfigurationSource s : sources) {
                    addSource(s);
                }
            }
            return this;
        }

        public Builder registerKey(ConfigurationKey<?> key) {
            if (key != null) {
                registeredKeys.add(key);
            }
            return this;
        }

        public Builder registerKeys(Collection<ConfigurationKey<?>> keys) {
            if (keys != null) {
                registeredKeys.addAll(keys);
            }
            return this;
        }

        public Builder withOverride(String key, String value) {
            Objects.requireNonNull(key, "key cannot be null");
            if (value != null) {
                manualOverrides.put(key, value);
            } else {
                manualOverrides.remove(key);
            }
            return this;
        }

        public Builder withOverrides(Map<String, String> overrides) {
            if (overrides != null) {
                manualOverrides.putAll(overrides);
            }
            return this;
        }

        public DefaultConfiguration build() {
            // Sort sources by ascending priority so higher priority sources override lower ones
            List<ConfigurationSource> sorted = new ArrayList<>(sources);
            sorted.sort(Comparator.comparingInt(ConfigurationSource::priority));

            Map<String, String> merged = new LinkedHashMap<>();
            for (ConfigurationSource source : sorted) {
                Map<String, String> loaded = source.load();
                if (loaded != null) {
                    merged.putAll(loaded);
                }
            }

            // Apply explicit manual overrides (priority highest: 600+)
            merged.putAll(manualOverrides);

            DefaultConfigurationSnapshot snapshot = new DefaultConfigurationSnapshot(
                    merged,
                    registeredKeys,
                    java.time.Instant.now()
            );

            return new DefaultConfiguration(snapshot);
        }
    }
}
