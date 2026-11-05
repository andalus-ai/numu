package tech.kayys.numou.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.api.*;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRuntimeHostPluginIntegrationTest {
    static class TestPlugin implements NumouPlugin {
        private final String id;
        private final PluginManifest manifest;
        private final List<String> events;
        private PluginState state = PluginState.LOADED;

        TestPlugin(String id, String capability, List<PluginDependency> dependencies, List<String> events) {
            this.id = id;
            this.events = events;
            this.manifest = PluginManifest.builder(id)
                    .capabilities(Set.of(Capability.of(capability)))
                    .dependencies(dependencies)
                    .build();
        }

        public String id() { return id; }
        public PluginManifest manifest() { return manifest; }
        public PluginState state() { return state; }

        @Override public void initialize(PluginContext context) {
            events.add("init:" + id);
            state = PluginState.INITIALIZED;
        }
        @Override public void start() {
            events.add("start:" + id);
            state = PluginState.ACTIVE;
        }
        @Override public void stop() {
            events.add("stop:" + id);
            state = PluginState.STOPPED;
        }
    }

    @Test
    void hostStartsOnlyResolvedApplicationPluginsAndStopsInReverseOrder() {
        List<String> events = new ArrayList<>();
        TestPlugin ledger = new TestPlugin("plugin.ledger", "ledger", List.of(), events);
        TestPlugin reporting = new TestPlugin("plugin.reporting", "reporting", List.of(), events);
        TestPlugin unrelated = new TestPlugin("plugin.hr", "hr", List.of(), events);

        var manifest = new ApplicationManifest("accounting", "1.0.0", "numou", "Accounting",
                List.of(new CapabilityRequirement("ledger", "^1.0.0")),
                List.of(new CapabilityRequirement("reporting", "^1.0.0")),
                Set.of(), List.of());
        var providers = List.of(
                new CapabilityProvider("ledger-runtime", "ledger", "1.0.0", List.of()),
                new CapabilityProvider("reporting-runtime", "reporting", "1.0.0",
                        List.of(new CapabilityRequirement("ledger", "^1.0.0"))));

        var factory = ApplicationPluginRuntimeFactory.builder()
                .register("ledger-runtime", "ledger", "1.0.0", ledger, Map.of())
                .register("reporting-runtime", "reporting", "1.0.0", reporting, Map.of())
                .build();
        var host = new ApplicationRuntimeHost(manifest, providers,
                new ApplicationCapabilityResolver(), factory);

        host.start();

        assertThat(host.activeProviderIds()).containsExactly("ledger-runtime", "reporting-runtime");
        assertThat(ledger.state()).isEqualTo(PluginState.ACTIVE);
        assertThat(reporting.state()).isEqualTo(PluginState.ACTIVE);
        assertThat(unrelated.state()).isEqualTo(PluginState.LOADED);
        assertThat(events).containsExactly(
                "init:plugin.ledger", "start:plugin.ledger",
                "init:plugin.reporting", "start:plugin.reporting");

        host.stop();

        assertThat(events).containsExactly(
                "init:plugin.ledger", "start:plugin.ledger",
                "init:plugin.reporting", "start:plugin.reporting",
                "stop:plugin.reporting", "stop:plugin.ledger");
        assertThat(unrelated.state()).isEqualTo(PluginState.LOADED);
    }
}
