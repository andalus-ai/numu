package tech.kayys.numou.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolutionReport;
import tech.kayys.numou.plugin.application.runtime.ApplicationComposition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationRuntimeHostCompositionTest {
    private static ApplicationManifest manifest() {
        return new ApplicationManifest("accounting", "1.0.0", "numou", "Accounting",
                List.of(new CapabilityRequirement("ledger", "^1.0.0")),
                List.of(new CapabilityRequirement("reporting", "^1.0.0")), Set.of(), List.of());
    }

    private static CapabilityProvider provider(String id, String capability) {
        return new CapabilityProvider(id, capability, "1.0.0", List.of());
    }

    private static ApplicationComposition composition(CapabilityProvider provider) {
        return new ApplicationComposition("accounting", "1.0.0", List.of(provider),
                new CapabilityResolutionReport(List.of(provider), List.of(),
                        List.of("reporting"), List.of("pre-resolved by application composition service")));
    }

    @Test
    void startsExactlyTheProvidersInTheSuppliedCompositionPlan() {
        CapabilityProvider selected = provider("trusted-ledger-v1", "ledger");
        List<String> events = new ArrayList<>();
        ApplicationRuntimeHost host = new ApplicationRuntimeHost(manifest(), composition(selected),
                resolved -> runtime(resolved.providerId(), events));

        host.start();

        assertThat(host.state()).isEqualTo(ApplicationRuntimeState.RUNNING);
        assertThat(host.activeProviderIds()).containsExactly("trusted-ledger-v1");
        assertThat(host.unavailableOptionalCapabilities()).containsExactly("reporting");
        assertThat(host.composition().initializationOrder()).containsExactly(selected);

        host.stop();

        assertThat(events).containsExactly("start:trusted-ledger-v1", "stop:trusted-ledger-v1");
        assertThat(host.state()).isEqualTo(ApplicationRuntimeState.STOPPED);
    }

    @Test
    void rejectsCompositionForAnotherApplication() {
        ApplicationComposition foreign = new ApplicationComposition("workforce", "1.0.0",
                List.of(provider("workforce-v1", "workforce")),
                new CapabilityResolutionReport(List.of(), List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> new ApplicationRuntimeHost(manifest(), foreign,
                ignored -> runtime("unused", new ArrayList<>())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("composition ID does not match");
    }

    @Test
    void rejectsCompositionForAnotherApplicationVersion() {
        ApplicationComposition foreign = new ApplicationComposition("accounting", "2.0.0",
                List.of(provider("ledger-v2", "ledger")),
                new CapabilityResolutionReport(List.of(), List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> new ApplicationRuntimeHost(manifest(), foreign,
                ignored -> runtime("unused", new ArrayList<>())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("composition version does not match");
    }

    private static CapabilityRuntime runtime(String providerId, List<String> events) {
        return new CapabilityRuntime() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public void start() {
                events.add("start:" + providerId);
            }

            @Override
            public void stop() {
                events.add("stop:" + providerId);
            }
        };
    }
}
