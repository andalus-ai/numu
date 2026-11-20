package tech.kayys.numou.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigurationTest {
    @Test
    void environmentOverridesPropertiesAndProfileDefaultsToStandalone() {
        Properties properties = new Properties();
        properties.setProperty(RuntimeConfiguration.APPLICATION_ID, "accounting");
        properties.setProperty(RuntimeConfiguration.PROFILE, "modular-monolith");

        RuntimeConfiguration configuration = RuntimeConfiguration.from(properties,
                Map.of("NUMOU_RUNTIME_PROFILE", "standalone", "NUMOU_RUNTIME_HTTP_PORT", "8080"));

        assertThat(configuration.applicationId()).isEqualTo("accounting");
        assertThat(configuration.profile()).isEqualTo("standalone");
        assertThat(configuration.value("numou.runtime.http-port")).contains("8080");
    }

    @Test
    void requiresApplicationId() {
        assertThatThrownBy(() -> RuntimeConfiguration.from(new Properties(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RuntimeConfiguration.APPLICATION_ID);
    }

    @Test
    void exposesImmutableConfiguration() {
        Properties properties = new Properties();
        properties.setProperty(RuntimeConfiguration.APPLICATION_ID, "accounting");
        RuntimeConfiguration configuration = RuntimeConfiguration.from(properties, Map.of());

        assertThatThrownBy(() -> configuration.values().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
