package tech.kayys.numou.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationRuntimeBootstrapTest {
    private static RuntimeConfiguration configuration(String id) {
        Properties properties = new Properties();
        properties.setProperty(RuntimeConfiguration.APPLICATION_ID, id);
        return RuntimeConfiguration.from(properties, Map.of());
    }

    private static ApplicationManifest manifest(String id) {
        return new ApplicationManifest(id, "1.0.0", "numou", id,
                List.of(new CapabilityRequirement("ledger", "^1.0.0")),
                List.of(), java.util.Set.of(), List.of());
    }

    @Test
    void transitionsThroughReadyAndStoppedAndReportsHealth() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        var provider = new CapabilityProvider("ledger-provider", "ledger", "1.0.0", List.of());
        CapabilityRuntimeFactory factory = ignored -> new CapabilityRuntime() {
            public String providerId() { return "ledger-provider"; }
            public void start() { starts.incrementAndGet(); }
            public void stop() { stops.incrementAndGet(); }
        };
        var definition = RuntimeApplicationDefinition.of(manifest("accounting"),
                List.of(provider), factory);
        var bootstrap = new ApplicationRuntimeBootstrap(configuration("accounting"), definition);

        assertThat(bootstrap.health().readiness()).isEqualTo(RuntimeReadiness.STARTING);
        assertThat(bootstrap.health().live()).isTrue();
        assertThat(bootstrap.health().ready()).isFalse();

        bootstrap.start();

        assertThat(bootstrap.health().readiness()).isEqualTo(RuntimeReadiness.READY);
        assertThat(bootstrap.health().ready()).isTrue();
        assertThat(bootstrap.health().activeProviderIds()).containsExactly("ledger-provider");
        assertThat(starts).hasValue(1);

        bootstrap.close();
        bootstrap.close();

        assertThat(bootstrap.health().readiness()).isEqualTo(RuntimeReadiness.STOPPED);
        assertThat(bootstrap.health().live()).isFalse();
        assertThat(stops).hasValue(1);
    }

    @Test
    void reportsFailedStartupAndDoesNotBecomeReady() {
        var provider = new CapabilityProvider("ledger-provider", "ledger", "1.0.0", List.of());
        CapabilityRuntimeFactory factory = ignored -> new CapabilityRuntime() {
            public String providerId() { return "ledger-provider"; }
            public void start() { throw new IllegalStateException("database unavailable"); }
        };
        var definition = RuntimeApplicationDefinition.of(manifest("accounting"),
                List.of(provider), factory);
        var bootstrap = new ApplicationRuntimeBootstrap(configuration("accounting"), definition);

        assertThatThrownBy(bootstrap::start).isInstanceOf(ApplicationRuntimeException.class);
        assertThat(bootstrap.health().readiness()).isEqualTo(RuntimeReadiness.FAILED);
        assertThat(bootstrap.health().ready()).isFalse();
        assertThat(bootstrap.health().failure()).contains("Failed to start application runtime");
    }

    @Test
    void rejectsConfigurationManifestIdentityMismatch() {
        var definition = RuntimeApplicationDefinition.of(manifest("accounting"), List.of(), ignored -> null);

        assertThatThrownBy(() -> new ApplicationRuntimeBootstrap(configuration("workforce"), definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match manifest");
    }
}
