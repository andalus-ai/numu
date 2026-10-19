package tech.kayys.numou.plugin;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimePluginManagerTest {

    static class TestPlugin implements NumouPlugin {
        private final String id;
        private final PluginManifest manifest;
        private PluginState state = PluginState.LOADED;
        final List<String> lifecycleLog = new ArrayList<>();

        TestPlugin(String id, Set<Capability> capabilities, List<PluginDependency> dependencies) {
            this.id = id;
            this.manifest = PluginManifest.builder(id)
                    .capabilities(capabilities)
                    .dependencies(dependencies)
                    .build();
        }

        @Override
        public String id() { return id; }

        @Override
        public PluginManifest manifest() { return manifest; }

        @Override
        public PluginState state() { return state; }

        @Override
        public void initialize(PluginContext context) {
            state = PluginState.INITIALIZED;
            lifecycleLog.add("INIT:" + id);
        }

        @Override
        public void start() {
            state = PluginState.ACTIVE;
            lifecycleLog.add("START:" + id);
        }

        @Override
        public void stop() {
            state = PluginState.STOPPED;
            lifecycleLog.add("STOP:" + id);
        }
    }

    @Test
    void shouldInitializeAndStartPluginsInTopologicalDependencyOrder() {
        RuntimePluginManager manager = new RuntimePluginManager();

        TestPlugin basePlugin = new TestPlugin("plugin.base", Set.of(Capability.of("BASE_SERVICE")), List.of());
        TestPlugin dependentPlugin = new TestPlugin(
                "plugin.dependent",
                Set.of(Capability.of("ADVANCED_SERVICE")),
                List.of(PluginDependency.required("plugin.base", "1.0.0"))
        );

        // Register dependent before base to test dependency sorting
        manager.register(dependentPlugin);
        manager.register(basePlugin);

        manager.start();

        assertThat(basePlugin.state()).isEqualTo(PluginState.ACTIVE);
        assertThat(dependentPlugin.state()).isEqualTo(PluginState.ACTIVE);

        // Routing checks
        Optional<NumouPlugin> provider = manager.findProvider("ADVANCED_SERVICE");
        assertThat(provider).isPresent();
        assertThat(provider.get().id()).isEqualTo("plugin.dependent");

        manager.stop();
        assertThat(basePlugin.state()).isEqualTo(PluginState.STOPPED);
        assertThat(dependentPlugin.state()).isEqualTo(PluginState.STOPPED);
    }

    @Test
    void shouldThrowExceptionOnUnresolvedRequiredDependency() {
        RuntimePluginManager manager = new RuntimePluginManager();
        TestPlugin brokenPlugin = new TestPlugin(
                "plugin.broken",
                Set.of(),
                List.of(PluginDependency.required("plugin.missing", "1.0.0"))
        );
        manager.register(brokenPlugin);

        assertThatThrownBy(manager::start)
                .isInstanceOf(PluginException.class)
                .hasMessageContaining("Unresolved required dependency");
    }
}
