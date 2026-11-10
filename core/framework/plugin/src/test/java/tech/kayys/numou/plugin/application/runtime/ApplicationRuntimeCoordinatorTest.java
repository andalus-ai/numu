package tech.kayys.numou.plugin.application.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.plugin.api.*;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationRuntimeCoordinatorTest {

    private static final class TestPlugin implements NumouPlugin {
        private final String id;
        private final PluginManifest manifest;
        private final List<String> events;
        private final String failureAt;
        private PluginContext receivedContext;
        private Object observedSharedService;

        private TestPlugin(String id, String capability, String contractVersion,
                List<String> events, String failureAt) {
            this.id = id;
            this.events = events;
            this.failureAt = failureAt;
            this.manifest = PluginManifest.builder(id)
                    .version("9.8.7") // Plugin release is deliberately independent from capability contract.
                    .capabilities(Set.of(Capability.of(capability, contractVersion)))
                    .build();
        }

        @Override public String id() { return id; }
        @Override public PluginManifest manifest() { return manifest; }
        @Override public PluginState state() { return PluginState.LOADED; }

        @Override
        public void initialize(PluginContext context) {
            events.add("init:" + id);
            receivedContext = context;
            if (id.equals("runtime.party.impl")) {
                context.registerService(SharedMarker.class, new SharedMarker());
            } else {
                observedSharedService = context.getService(SharedMarker.class).orElse(null);
            }
            if ("initialize".equals(failureAt)) {
                throw new IllegalStateException("initialize failed");
            }
        }

        @Override
        public void start() {
            events.add("start:" + id);
            if ("start".equals(failureAt)) {
                throw new IllegalStateException("start failed");
            }
        }

        @Override
        public void stop() {
            events.add("stop:" + id);
        }
    }

    private static final class SharedMarker {
    }

    private ApplicationComposition composition() {
        var manifest = new ApplicationManifest("com.example.erp", "1.0.0",
                "com.example", "ERP", List.of(
                new CapabilityRequirement("numou.party", "^1.0.0"),
                new CapabilityRequirement("numou.accounting", "^2.0.0")),
                List.of(), Set.of(), List.of());
        var party = new CapabilityProvider("party-provider", "numou.party", "1.2.0", List.of());
        var accounting = new CapabilityProvider("accounting-provider", "numou.accounting", "2.1.0",
                List.of(new CapabilityRequirement("numou.party", "^1.0.0")));
        return new ApplicationCompositionService().compose(manifest, List.of(accounting, party));
    }

    @Test
    void bindsByProviderIdAndExecutesM3OrderRatherThanPluginIdOrVersion() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);

        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting));

        assertThat(events).containsExactly("init:runtime.party.impl", "init:runtime.ledger.impl",
                "start:runtime.party.impl", "start:runtime.ledger.impl");
        assertThat(session.providerIds()).containsExactly("party-provider", "accounting-provider");
        session.stop();
        session.stop();
        assertThat(events).endsWith("stop:runtime.ledger.impl", "stop:runtime.party.impl");
        assertThat(session.isStopped()).isTrue();
    }

    @Test
    void rejectsMissingRuntimeImplementationBeforeInvokingLifecycle() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);

        assertThatThrownBy(() -> new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party)))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("Missing runtime implementation for provider accounting-provider");
        assertThat(events).isEmpty();
    }

    @Test
    void rejectsCapabilityContractMismatchWithoutConfusingPluginReleaseVersion() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.3.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);

        assertThatThrownBy(() -> new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting)))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("selected contract version 1.2.0");
        assertThat(events).isEmpty();
    }

    @Test
    void rollsBackInitializedPluginsInReverseOrderWhenStartFails() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, "start");

        assertThatThrownBy(() -> new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting)))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("Failed to start application com.example.erp at provider accounting-provider")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(events).containsExactly("init:runtime.party.impl", "init:runtime.ledger.impl",
                "start:runtime.party.impl", "start:runtime.ledger.impl",
                "stop:runtime.ledger.impl", "stop:runtime.party.impl");
    }

    @Test
    void attemptsCleanupOfPluginWhoseInitializationFails() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, "initialize");
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);

        assertThatThrownBy(() -> new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting)))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("party-provider");
        assertThat(events).containsExactly("init:runtime.party.impl", "stop:runtime.party.impl");
    }

    @Test
    void rejectsOneRuntimeInstanceBoundToMultipleProvidersBeforeLifecycle() {
        List<String> events = new ArrayList<>();
        TestPlugin shared = new TestPlugin("runtime.shared.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);

        assertThatThrownBy(() -> new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", shared, "accounting-provider", shared)))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("Runtime plugin instance is bound to more than one provider");
        assertThat(events).isEmpty();
    }

    @Test
    void hostContextSharesConfigurationAndServicesAcrossPluginContexts() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);
        DefaultApplicationPluginContextFactory contexts =
                new DefaultApplicationPluginContextFactory(Map.of("region", "id-ID"));
        contexts.registerService(Runnable.class, () -> { });

        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting), contexts);

        assertThat(party.receivedContext.getProperty("region")).contains("id-ID");
        assertThat(accounting.receivedContext.getProperty("region")).contains("id-ID");
        assertThat(accounting.observedSharedService).isInstanceOf(SharedMarker.class);
        assertThat(session.context("party-provider")).contains(party.receivedContext);
        assertThat(session.context("accounting-provider")).contains(accounting.receivedContext);
        assertThat(session.contexts()).containsKeys("party-provider", "accounting-provider");
        session.stop();
    }

    @Test
    void rejectsHostContextForWrongPluginBeforeCallingInitialize() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);

        assertThatThrownBy(() -> new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting),
                (provider, plugin) -> new DefaultPluginContext("wrong-plugin")))
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("Context factory must return a context for plugin runtime.party.impl");
        assertThat(events).isEmpty();
    }
}
