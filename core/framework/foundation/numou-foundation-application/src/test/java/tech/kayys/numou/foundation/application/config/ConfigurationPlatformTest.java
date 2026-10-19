package tech.kayys.numou.foundation.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Configuration & Snapshot Tests")
class ConfigurationPlatformTest {

    @Test
    @DisplayName("Should parse typed keys and apply defaults")
    void shouldParseTypedKeysAndDefaults() {
        ConfigurationKey<String> nameKey = ConfigurationKey.optional("app.name", String.class, "default-app");
        ConfigurationKey<Integer> portKey = ConfigurationKey.optional("app.port", Integer.class, 8080);
        ConfigurationKey<Duration> timeoutKey = ConfigurationKey.optional("app.timeout", Duration.class, Duration.ofSeconds(10));

        DefaultConfigurationSnapshot snapshot = new DefaultConfigurationSnapshot(Map.of(
                "app.name", "numou-service",
                "app.timeout", "30s"
        ));

        assertEquals("numou-service", snapshot.get(nameKey).orElse(null));
        assertEquals(8080, snapshot.get(portKey).orElse(null));
        assertEquals(Duration.ofSeconds(30), snapshot.get(timeoutKey).orElse(null));
    }

    @Test
    @DisplayName("Should enforce required configuration keys")
    void shouldEnforceRequiredKeys() {
        ConfigurationKey<String> requiredKey = ConfigurationKey.required("db.url", String.class);
        Configuration config = DefaultConfiguration.builder()
                .withOverride("app.name", "numou")
                .build();

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> config.require(requiredKey));
        assertTrue(ex.getMessage().contains("db.url"));
    }

    @Test
    @DisplayName("Should redact sensitive keys in toRedactedMap")
    void shouldRedactSensitiveKeys() {
        ConfigurationKey<String> secretKey = ConfigurationKey.builder("payment.api-key", String.class)
                .sensitive()
                .build();

        DefaultConfigurationSnapshot snapshot = new DefaultConfigurationSnapshot(
                Map.of(
                        "payment.api-key", "secret-token-123",
                        "database.password", "super-secret",
                        "app.name", "numou"
                ),
                Set.of(secretKey),
                java.time.Instant.now()
        );

        Map<String, String> redacted = snapshot.toRedactedMap();
        assertEquals("********", redacted.get("payment.api-key"));
        assertEquals("********", redacted.get("database.password"));
        assertEquals("numou", redacted.get("app.name"));
    }

    @Test
    @DisplayName("Should merge configuration sources according to priority")
    void shouldMergeSourcesAccordingToPriority() {
        ConfigurationSource lowPriority = new ConfigurationSource() {
            @Override
            public String name() { return "low"; }
            @Override
            public int priority() { return 100; }
            @Override
            public Map<String, String> load() {
                return Map.of("key1", "val1-low", "key2", "val2-low");
            }
        };

        ConfigurationSource highPriority = new ConfigurationSource() {
            @Override
            public String name() { return "high"; }
            @Override
            public int priority() { return 200; }
            @Override
            public Map<String, String> load() {
                return Map.of("key2", "val2-high", "key3", "val3-high");
            }
        };

        Configuration config = DefaultConfiguration.builder()
                .addSource(lowPriority)
                .addSource(highPriority)
                .withOverride("key3", "val3-override")
                .build();

        assertEquals("val1-low", config.get("key1").orElse(null));
        assertEquals("val2-high", config.get("key2").orElse(null));
        assertEquals("val3-override", config.get("key3").orElse(null));
    }
}
