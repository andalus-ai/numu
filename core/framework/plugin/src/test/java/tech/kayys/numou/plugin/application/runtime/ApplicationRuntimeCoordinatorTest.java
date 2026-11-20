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
        private Object registeredService;
        private PluginState state = PluginState.LOADED;
        private int stopFailuresRemaining;

        private TestPlugin(String id, String capability, String contractVersion,
                List<String> events, String failureAt) {
            this.id = id;
            this.events = events;
            this.failureAt = failureAt;
            this.stopFailuresRemaining = "stop".equals(failureAt) ? 1 : 0;
            this.manifest = PluginManifest.builder(id)
                    .version("9.8.7") // Plugin release is deliberately independent from capability contract.
                    .capabilities(Set.of(Capability.of(capability, contractVersion)))
                    .build();
        }

        @Override public String id() { return id; }
        @Override public PluginManifest manifest() { return manifest; }
        @Override public PluginState state() { return state; }

        @Override
        public void initialize(PluginContext context) {
            events.add("init:" + id);
            state = PluginState.INITIALIZED;
            receivedContext = context;
            if (id.startsWith("runtime.party")) {
                registeredService = new SharedMarker();
                context.registerService(SharedMarker.class, registeredService);
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
            state = PluginState.ACTIVE;
        }

        @Override
        public void stop() {
            events.add("stop:" + id);
            if (stopFailuresRemaining > 0) {
                stopFailuresRemaining--;
                throw new IllegalStateException("stop failed once");
            }
            state = PluginState.STOPPED;
        }
    }

    private static final class SharedMarker {
    }

    private ApplicationComposition composition() {
        return composition("com.example.erp");
    }

    private ApplicationComposition composition(String applicationId) {
        var manifest = new ApplicationManifest(applicationId, "1.0.0",
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
                new DefaultApplicationPluginContextFactory(Map.of("region", "id-ID"),
                        Map.of("tenant.id", "tenant-42", "actor.id", "user-7",
                                "correlation.id", "corr-abc"));
        contexts.registerService(Runnable.class, () -> { });

        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting), contexts);

        assertThat(party.receivedContext.getProperty("region")).contains("id-ID");
        assertThat(accounting.receivedContext.getProperty("region")).contains("id-ID");
        assertThat(party.receivedContext.attributes())
                .containsEntry("tenant.id", "tenant-42")
                .containsEntry("actor.id", "user-7")
                .containsEntry("correlation.id", "corr-abc");
        assertThat(accounting.receivedContext.attributes())
                .containsEntry("tenant.id", "tenant-42")
                .containsEntry("actor.id", "user-7");
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
    @Test
    void routesOnlyToTheProviderSelectedForThisApplicationAndReportsReadiness() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);
        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting));

        assertThat(session.route("numou.party")).contains(party);
        assertThat(session.route("numou.accounting")).contains(accounting);
        assertThat(session.route("numou.unknown")).isEmpty();
        assertThat(session.health().ready()).isTrue();
        assertThat(session.health().stopped()).isFalse();
        assertThat(session.health().providers()).hasSize(2)
                .allMatch(ApplicationRuntimeProviderHealth::ready);

        session.stop();
        assertThat(session.route("numou.party")).isEmpty();
        assertThat(session.health().ready()).isFalse();
        assertThat(session.health().stopped()).isTrue();
        assertThat(session.health().providers()).noneMatch(ApplicationRuntimeProviderHealth::ready);
    }

    @Test
    void runtimeReadinessIsFalseWhenASelectedProviderIsNotActive() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);
        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting));
        accounting.state = PluginState.FAILED;

        assertThat(session.route("numou.accounting")).isEmpty();
        assertThat(session.health().ready()).isFalse();
        assertThat(session.health().providers())
                .anyMatch(provider -> provider.providerId().equals("accounting-provider")
                        && provider.state() == PluginState.FAILED && !provider.ready());
        session.stop();
    }

    @Test
    void defaultContextFactoryIsolatesPluginServicesBetweenApplications() {
        List<String> events = new ArrayList<>();
        DefaultApplicationPluginContextFactory sharedFactory =
                new DefaultApplicationPluginContextFactory(Map.of("region", "id-ID"));
        TestPlugin partyOne = new TestPlugin("runtime.party.one", "numou.party", "1.2.0", events, null);
        TestPlugin accountingOne = new TestPlugin("runtime.accounting.one", "numou.accounting", "2.1.0", events, null);
        ApplicationRuntimeSession first = new ApplicationRuntimeCoordinator().start(composition("app-one"),
                Map.of("party-provider", partyOne, "accounting-provider", accountingOne), sharedFactory);

        TestPlugin partyTwo = new TestPlugin("runtime.party.two", "numou.party", "1.2.0", events, null);
        TestPlugin accountingTwo = new TestPlugin("runtime.accounting.two", "numou.accounting", "2.1.0", events, null);
        ApplicationRuntimeSession second = new ApplicationRuntimeCoordinator().start(composition("app-two"),
                Map.of("party-provider", partyTwo, "accounting-provider", accountingTwo), sharedFactory);

        assertThat(partyOne.receivedContext.attributes())
                .containsEntry("application.id", "app-one");
        assertThat(partyTwo.receivedContext.attributes())
                .containsEntry("application.id", "app-two");
        assertThat(accountingOne.observedSharedService).isSameAs(partyOne.registeredService);
        assertThat(accountingTwo.observedSharedService).isSameAs(partyTwo.registeredService);
        assertThat(accountingOne.observedSharedService).isNotSameAs(accountingTwo.observedSharedService);
        assertThat(first.context("party-provider")).isNotSameAs(second.context("party-provider"));
        first.stop();
        second.stop();
    }

    @Test
    void retriesOnlyFailedProvidersDuringGracefulShutdown() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, "stop");
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);
        List<ApplicationRuntimeLifecycleEvent.Type> lifecycleEvents = new ArrayList<>();
        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting),
                new DefaultApplicationPluginContextFactory(),
                event -> lifecycleEvents.add(event.type()));

        assertThatThrownBy(session::stop)
                .isInstanceOf(ApplicationRuntimeException.class)
                .hasMessageContaining("1/2 providers stopped");
        assertThat(session.health().providers())
                .anyMatch(provider -> provider.providerId().equals("party-provider")
                        && provider.failure().contains("stop failed once"));
        assertThat(session.isStopping()).isTrue();
        assertThat(session.isStopped()).isFalse();
        assertThat(session.route("numou.party")).isEmpty();

        session.stop();
        assertThat(session.isStopped()).isTrue();
        assertThat(session.health().providers())
                .allMatch(provider -> provider.failure() == null);
        assertThat(lifecycleEvents).containsSubsequence(
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOPPED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOP_FAILED,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_FAILED,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOPPED,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_COMPLETED);
        assertThat(events).containsExactly("init:runtime.party.impl", "init:runtime.ledger.impl",
                "start:runtime.party.impl", "start:runtime.ledger.impl",
                "stop:runtime.ledger.impl", "stop:runtime.party.impl", "stop:runtime.party.impl");
        session.stop();
        assertThat(events).hasSize(7);
    }

    @Test
    void tenantBoundContextFactoryCannotBeReusedForAnotherApplication() {
        DefaultApplicationPluginContextFactory tenantFactory =
                new DefaultApplicationPluginContextFactory(Map.of(), Map.of("tenant.id", "tenant-42"));
        assertThat(tenantFactory.forApplication("app-one")).isNotNull();
        assertThatThrownBy(() -> tenantFactory.forApplication("app-two"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant-bound context factory is already assigned to application app-one");
    }


    @Test
    void emitsDeterministicLifecycleEventsAcrossStartupAndShutdown() {
        List<String> pluginEvents = new ArrayList<>();
        List<ApplicationRuntimeLifecycleEvent> observed = new ArrayList<>();
        var fixedInstant = java.time.Instant.parse("2026-11-20T03:00:00Z");
        var clock = java.time.Clock.fixed(fixedInstant, java.time.ZoneOffset.UTC);
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", pluginEvents, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", pluginEvents, null);

        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator(clock).start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting),
                new DefaultApplicationPluginContextFactory(), observed::add);
        session.stop();

        assertThat(observed).extracting(ApplicationRuntimeLifecycleEvent::type).containsExactly(
                ApplicationRuntimeLifecycleEvent.Type.APPLICATION_STARTING,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_INITIALIZATION_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_INITIALIZED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_INITIALIZATION_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_INITIALIZED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_START_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_START_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOPPED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STOPPED,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_COMPLETED);
        assertThat(observed).allSatisfy(event -> {
            assertThat(event.applicationId()).isEqualTo("com.example.erp");
            assertThat(event.occurredAt()).isEqualTo(fixedInstant);
        });
        assertThat(observed.get(1).providerId()).isEqualTo("party-provider");
        assertThat(observed.get(10).providerId()).isEqualTo("accounting-provider");
    }

    @Test
    void observerFailuresNeverInterruptStartupRollbackOrShutdown() {
        List<String> pluginEvents = new ArrayList<>();
        List<ApplicationRuntimeLifecycleEvent.Type> attemptedObservations = new ArrayList<>();
        ApplicationRuntimeLifecycleObserver unreliableObserver = event -> {
            attemptedObservations.add(event.type());
            throw new IllegalStateException("telemetry unavailable");
        };
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", pluginEvents, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0",
                pluginEvents, null);

        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting),
                new DefaultApplicationPluginContextFactory(), unreliableObserver);
        assertThat(session.health().ready()).isTrue();
        session.stop();

        assertThat(session.isStopped()).isTrue();
        assertThat(attemptedObservations).contains(
                ApplicationRuntimeLifecycleEvent.Type.APPLICATION_STARTING,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.SHUTDOWN_COMPLETED);
        assertThat(pluginEvents).contains("start:runtime.party.impl", "start:runtime.ledger.impl",
                "stop:runtime.ledger.impl", "stop:runtime.party.impl");

        List<String> rollbackPluginEvents = new ArrayList<>();
        List<ApplicationRuntimeLifecycleEvent.Type> rollbackObservations = new ArrayList<>();
        TestPlugin failingParty = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0",
                rollbackPluginEvents, null);
        TestPlugin failingAccounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0",
                rollbackPluginEvents, "start");
        assertThatThrownBy(() -> new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", failingParty, "accounting-provider", failingAccounting),
                new DefaultApplicationPluginContextFactory(), event -> {
                    rollbackObservations.add(event.type());
                    throw new IllegalStateException("telemetry unavailable");
                })).isInstanceOf(ApplicationRuntimeException.class);
        assertThat(rollbackPluginEvents).containsExactly("init:runtime.party.impl", "init:runtime.ledger.impl",
                "start:runtime.party.impl", "start:runtime.ledger.impl",
                "stop:runtime.ledger.impl", "stop:runtime.party.impl");
        assertThat(rollbackObservations).contains(
                ApplicationRuntimeLifecycleEvent.Type.STARTUP_FAILED,
                ApplicationRuntimeLifecycleEvent.Type.ROLLBACK_STARTED,
                ApplicationRuntimeLifecycleEvent.Type.PROVIDER_ROLLBACK_COMPLETED,
                ApplicationRuntimeLifecycleEvent.Type.ROLLBACK_COMPLETED);
    }
\n
    @Test
    void adaptsApplicationReadinessToSharedHealthCheckContract() {
        List<String> events = new ArrayList<>();
        TestPlugin party = new TestPlugin("runtime.party.impl", "numou.party", "1.2.0", events, null);
        TestPlugin accounting = new TestPlugin("runtime.ledger.impl", "numou.accounting", "2.1.0", events, null);
        ApplicationRuntimeSession session = new ApplicationRuntimeCoordinator().start(composition(),
                Map.of("party-provider", party, "accounting-provider", accounting));
        var check = new ApplicationRuntimeHealthCheck(session);

        assertThat(check.id()).isEqualTo("application-runtime");
        assertThat(check.category()).isEqualTo(tech.kayys.numou.observability.HealthCheckCategory.RUNTIME);
        assertThat(check.criticality())
                .isEqualTo(tech.kayys.numou.observability.HealthCheckCriticality.REQUIRED);
        assertThat(check.check().await().indefinitely().status())
                .isEqualTo(tech.kayys.numou.observability.HealthStatus.Status.UP);

        accounting.state = PluginState.FAILED;
        var degradedRuntime = check.check().await().indefinitely();
        assertThat(degradedRuntime.status()).isEqualTo(tech.kayys.numou.observability.HealthStatus.Status.DOWN);
        assertThat(degradedRuntime.message()).contains("not ready: 1/2 providers");

        session.stop();
        var stoppedRuntime = check.check().await().indefinitely();
        assertThat(stoppedRuntime.status()).isEqualTo(tech.kayys.numou.observability.HealthStatus.Status.DOWN);
        assertThat(stoppedRuntime.message()).contains("stopped");
    }
\n}\n