package tech.kayys.numou.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ApplicationRuntimeHostTest {
    private static ApplicationManifest manifest() {
        return new ApplicationManifest("accounting", "1.0.0", "numou", "Accounting",
                List.of(new CapabilityRequirement("ledger", "^1.0.0")),
                List.of(new CapabilityRequirement("reporting", "^1.0.0")), Set.of(), List.of());
    }

    private static CapabilityProvider provider(String id, String capability, List<CapabilityRequirement> deps) {
        return new CapabilityProvider(id, capability, "1.0.0", deps);
    }

    @Test
    void startsInDependencyOrderAndStopsInReverseOrder() {
        var providers = List.of(
                provider("ledger-provider", "ledger", List.of(
                        new CapabilityRequirement("party", "^1.0.0"))),
                provider("party-provider", "party", List.of()));
        var events = new ArrayList<String>();
        var host = new ApplicationRuntimeHost(manifest(), providers,
                new ApplicationCapabilityResolver(),
                p -> runtime(p.providerId(), events, false));

        host.start();

        assertThat(host.state()).isEqualTo(ApplicationRuntimeState.RUNNING);
        assertThat(host.activeProviderIds()).containsExactly("party-provider", "ledger-provider");
        assertThat(host.unavailableOptionalCapabilities()).containsExactly("reporting");

        host.stop();
        host.stop();

        assertThat(events).containsExactly(
                "start:party-provider", "start:ledger-provider",
                "stop:ledger-provider", "stop:party-provider");
        assertThat(host.state()).isEqualTo(ApplicationRuntimeState.STOPPED);
    }

    @Test
    void rollsBackAlreadyStartedRuntimesWhenLaterProviderFails() {
        var providers = List.of(
                provider("party-provider", "party", List.of()),
                provider("ledger-provider", "ledger", List.of(
                        new CapabilityRequirement("party", "^1.0.0"))));
        var events = new ArrayList<String>();
        var host = new ApplicationRuntimeHost(manifest(), providers,
                new ApplicationCapabilityResolver(),
                p -> runtime(p.providerId(), events, p.providerId().equals("ledger-provider")));

        assertThatThrownBy(host::start)
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("Failed to start application runtime accounting");

        assertThat(host.state()).isEqualTo(ApplicationRuntimeState.FAILED);
        assertThat(host.activeProviderIds()).isEmpty();
        assertThat(events).containsExactly(
                "start:party-provider", "start:ledger-provider", "stop:party-provider");
    }

    @Test
    void rejectsStartingTwiceAndRejectsFactoryProviderMismatch() {
        var providers = List.of(provider("ledger-provider", "ledger", List.of()));
        var host = new ApplicationRuntimeHost(manifest(), providers,
                new ApplicationCapabilityResolver(),
                p -> runtime("wrong-provider", new ArrayList<>(), false));

        assertThatThrownBy(host::start).isInstanceOf(ApplicationRuntimeException.class);
        assertThatThrownBy(host::start).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only start from NEW");
    }

    private static CapabilityRuntime runtime(String id, List<String> events, boolean failStart) {
        return new CapabilityRuntime() {
            @Override public String providerId() { return id; }
            @Override public void start() {
                events.add("start:" + id);
                if (failStart) throw new IllegalStateException("simulated startup failure");
            }
            @Override public void stop() { events.add("stop:" + id); }
        };
    }
}
