package tech.kayys.numou.foundation.application.config;

import java.time.Instant;
import java.util.*;

/**
 * Immutable in-memory snapshot of configuration.
 */
public final class DefaultConfigurationSnapshot implements ConfigurationSnapshot {

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "secret", "token", "apikey", "api-key", "credential", "privatekey", "private-key"
    );

    private final Map<String, String> entries;
    private final Set<ConfigurationKey<?>> registeredKeys;
    private final Instant createdAt;

    public DefaultConfigurationSnapshot(Map<String, String> entries) {
        this(entries, Set.of(), Instant.now());
    }

    public DefaultConfigurationSnapshot(
            Map<String, String> entries,
            Set<ConfigurationKey<?>> registeredKeys,
            Instant createdAt) {

        this.entries = entries != null ? Collections.unmodifiableMap(new LinkedHashMap<>(entries)) : Map.of();
        this.registeredKeys = registeredKeys != null ? Set.copyOf(registeredKeys) : Set.of();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @Override
    public <T> Optional<T> get(ConfigurationKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        String raw = entries.get(key.name());
        if (raw != null) {
            try {
                return Optional.ofNullable(ConfigurationParsers.parse(raw, key.type()));
            } catch (RuntimeException e) {
                throw new ConfigurationException(
                        "Failed to parse configuration key [" + key.name() + "] as " + key.type().getSimpleName(), e);
            }
        }
        return key.defaultValue();
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public boolean contains(ConfigurationKey<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return entries.containsKey(key.name()) || key.defaultValue().isPresent();
    }

    @Override
    public boolean contains(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return entries.containsKey(key);
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Map<String, String> rawEntries() {
        return entries;
    }

    @Override
    public Map<String, String> toRedactedMap() {
        Map<String, String> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (isSensitive(entry.getKey())) {
                redacted.put(entry.getKey(), "********");
            } else {
                redacted.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(redacted);
    }

    private boolean isSensitive(String keyName) {
        for (ConfigurationKey<?> registered : registeredKeys) {
            if (registered.name().equalsIgnoreCase(keyName) && registered.sensitive()) {
                return true;
            }
        }
        String lower = keyName.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
