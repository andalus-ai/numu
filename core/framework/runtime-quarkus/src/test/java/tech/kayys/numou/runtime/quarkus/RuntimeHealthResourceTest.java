package tech.kayys.numou.runtime.quarkus;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.runtime.ApplicationRuntimeBootstrap;
import tech.kayys.numou.runtime.CapabilityRuntimeFactory;
import tech.kayys.numou.runtime.RuntimeApplicationDefinition;
import tech.kayys.numou.runtime.RuntimeConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeHealthResourceTest {

    @Test
    void reportsStartingAsLiveButNotReadyAndReadyAfterBootstrapStarts() {
        ApplicationRuntimeBootstrap bootstrap = bootstrap();
        RuntimeHealthResource resource = new RuntimeHealthResource(bootstrap);

        try {
            assertThat(resource.live().getStatus()).isEqualTo(200);
            assertThat(resource.ready().getStatus()).isEqualTo(503);

            bootstrap.start();

            assertThat(resource.live().getStatus()).isEqualTo(200);
            assertThat(resource.ready().getStatus()).isEqualTo(200);
        } finally {
            bootstrap.close();
        }
    }

    @Test
    void reportsStoppedProcessAsNotLiveAndNotReady() {
        ApplicationRuntimeBootstrap bootstrap = bootstrap();
        RuntimeHealthResource resource = new RuntimeHealthResource(bootstrap);

        bootstrap.close();

        assertThat(resource.live().getStatus()).isEqualTo(503);
        assertThat(resource.ready().getStatus()).isEqualTo(503);
    }

    private ApplicationRuntimeBootstrap bootstrap() {
        Properties properties = new Properties();
        properties.setProperty(RuntimeConfiguration.APPLICATION_ID, "accounting-test");
        RuntimeConfiguration configuration = RuntimeConfiguration.from(properties, Map.of());

        ApplicationManifest manifest = new ApplicationManifest(
                "accounting-test",
                "1.0.0",
                "numou",
                "Accounting Test",
                List.of(),
                List.of(),
                Set.of(),
                List.of());

        CapabilityRuntimeFactory factory = provider -> {
            throw new AssertionError("No provider should be requested for an empty test composition");
        };
        RuntimeApplicationDefinition definition =
                RuntimeApplicationDefinition.of(manifest, List.of(), factory);
        return new ApplicationRuntimeBootstrap(configuration, definition);
    }
}
