package tech.kayys.numou.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.api.*;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ApplicationPluginRuntimeFactoryTest {
    interface LedgerService {
        String name();
    }

    static final class LedgerServiceImpl implements LedgerService {
        public String name() { return "ledger"; }
    }

    static class TestPlugin implements NumouPlugin {
        private final String id;
        private final PluginManifest manifest;
        private final List<String> events;
        private PluginState state = PluginState.LOADED;
        private LedgerService serviceSeenAtStart;

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

        @Override
        public void initialize(PluginContext context) {
            events.add("init:" + id);
            state = PluginState.INITIALIZED;
            if (id.equals("ledger-plugin")) {
                context.registerService(LedgerService.class, new LedgerServiceImpl());
            } else {
                serviceSeenAtStart = context.getService(LedgerService.class).orElseThrow();
            }
        }

        @Override
        public void start() {
            events.add("start:" + id);
            state = PluginState.ACTIVE;
        }

        @Override
        public void stop() {
            events.add("stop:" + id);
            state = PluginState.STOPPED;
        }
    }

    private static CapabilityProvider provider(String providerId, String capability) {
        return new CapabilityProvider(providerId, capability, "1.0.0", List.of());
    }

    @Test
    void activatesOnlyExplicitlyRegisteredPluginAndSharesApplicationServices() throws Exception {
        List<String> events = new ArrayList<>();
        TestPlugin ledger = new TestPlugin("ledger-plugin", "LEDGER", List.of(), events);
        TestPlugin reporting = new TestPlugin("reporting-plugin", "REPORTING", List.of(), events);
        TestPlugin unrelated = new TestPlugin("unrelated-plugin", "HR", List.of(), events);

        var factory = ApplicationPluginRuntimeFactory.builder()
                .register("ledger-provider", "LEDGER", "1.0.0", ledger, Map.of("region", "id"))
                .register("reporting-provider", "REPORTING", "1.0.0", reporting, Map.of())
                .build();

        CapabilityRuntime ledgerRuntime = factory.create(provider("ledger-provider", "LEDGER"));
        CapabilityRuntime reportingRuntime = factory.create(provider("reporting-provider", "REPORTING"));
        ledgerRuntime.start();
        reportingRuntime.start();

        assertThat(events).containsExactly("init:ledger-plugin", "start:ledger-plugin",
                "init:reporting-plugin", "start:reporting-plugin");
        assertThat(reporting.serviceSeenAtStart).isNotNull();
        assertThat(reporting.serviceSeenAtStart.name()).isEqualTo("ledger");
        assertThat(unrelated.state()).isEqualTo(PluginState.LOADED);
    }

    @Test
    void stopsPluginAfterPartialInitializationFailure() {
        List<String> events = new ArrayList<>();
        NumouPlugin broken = new TestPlugin("broken-plugin", "LEDGER", List.of(), events) {
            @Override
            public void initialize(PluginContext context) {
                events.add("init:broken-plugin");
                throw new IllegalStateException("partial init");
            }
        };
        var factory = ApplicationPluginRuntimeFactory.builder()
                .register("ledger-provider", "LEDGER", "1.0.0", broken, Map.of())
                .build();
        CapabilityRuntime runtime = factory.create(provider("ledger-provider", "LEDGER"));

        assertThatThrownBy(runtime::start).isInstanceOf(IllegalStateException.class);
        assertThatCode(runtime::stop).doesNotThrowAnyException();
        assertThat(events).containsExactly("init:broken-plugin", "stop:broken-plugin");
    }

    @Test
    void rejectsCapabilityAndContractMismatch() {
        TestPlugin plugin = new TestPlugin("ledger-plugin", "LEDGER", List.of(), new ArrayList<>());
        var factory = ApplicationPluginRuntimeFactory.builder()
                .register("ledger-provider", "LEDGER", "1.0.0", plugin, Map.of())
                .build();

        assertThatThrownBy(() -> factory.create(provider("ledger-provider", "REPORTING")))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("Capability mismatch");
        assertThatThrownBy(() -> factory.create(
                new CapabilityProvider("ledger-provider", "LEDGER", "2.0.0", List.of())))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("Contract version mismatch");
    }

    @Test
    void rejectsPluginsThatDoNotAdvertiseCapabilityAndDuplicatePluginRegistration() {
        TestPlugin ledger = new TestPlugin("ledger-plugin", "LEDGER", List.of(), new ArrayList<>());
        var builder = ApplicationPluginRuntimeFactory.builder()
                .register("ledger-provider", "LEDGER", "1.0.0", ledger, Map.of());

        assertThatThrownBy(() -> builder.register("report-provider", "REPORTING", "1.0.0", ledger, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        assertThatThrownBy(() -> ApplicationPluginRuntimeFactory.builder()
                .register("bad-provider", "REPORTING", "1.0.0", ledger, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not advertise");
    }
}
