package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.PluginManifest;
import tech.kayys.numou.plugin.api.PluginState;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeApplicationHostCompositionAdapterTest {
    private static final String APP = "com.example.orders";
    private static final String DIGEST = "b".repeat(64);
    private static final TenantId TENANT = TenantId.generate();

    @Test
    void activatesInDependencyOrderAndStopsInReverseOrder() {
        List<String> lifecycle = new ArrayList<>();
        var registry = new ApplicationCapabilityProviderRuntimeRegistry();
        register(registry, provider("provider.storage", "storage", List.of()), TENANT::equals,
                () -> new FakePlugin("provider.storage", "storage", lifecycle, false));
        register(registry, provider("provider.orders", "orders",
                List.of(new CapabilityRequirement("storage", "^1.0.0"))), TENANT::equals,
                () -> new FakePlugin("provider.orders", "orders", lifecycle, false));
        var adapter = adapter(registry);
        var command = activation(TENANT, UUID.randomUUID(),
                manifest(List.of(new CapabilityRequirement("orders", "^1.0.0"))));

        adapter.activate(command).toCompletableFuture().join();
        assertThat(lifecycle).containsExactly("start:provider.storage", "start:provider.orders");
        assertThat(adapter.health(TENANT, command.installationId()))
                .isEqualTo(RuntimeApplicationHostCompositionAdapter.RuntimeHealth.READY);

        adapter.deactivate(deactivation(command)).toCompletableFuture().join();
        assertThat(lifecycle).containsExactly("start:provider.storage", "start:provider.orders",
                "stop:provider.orders", "stop:provider.storage");
        assertThat(adapter.health(TENANT, command.installationId()))
                .isEqualTo(RuntimeApplicationHostCompositionAdapter.RuntimeHealth.INACTIVE);
    }

    @Test
    void failedActivationRollsBackStartedProvidersInReverseOrder() {
        List<String> lifecycle = new ArrayList<>();
        var registry = new ApplicationCapabilityProviderRuntimeRegistry();
        register(registry, provider("provider.storage", "storage", List.of()), TENANT::equals,
                () -> new FakePlugin("provider.storage", "storage", lifecycle, false));
        register(registry, provider("provider.orders", "orders",
                List.of(new CapabilityRequirement("storage", "^1.0.0"))), TENANT::equals,
                () -> new FakePlugin("provider.orders", "orders", lifecycle, true));
        var adapter = adapter(registry);
        var command = activation(TENANT, UUID.randomUUID(),
                manifest(List.of(new CapabilityRequirement("orders", "^1.0.0"))));

        assertThatThrownBy(() -> adapter.activate(command).toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("start failure");
        assertThat(lifecycle).containsExactly("start:provider.storage", "start:provider.orders",
                "stop:provider.orders", "stop:provider.storage");
        assertThat(adapter.health(TENANT, command.installationId()))
                .isEqualTo(RuntimeApplicationHostCompositionAdapter.RuntimeHealth.INACTIVE);
    }

    @Test
    void tenantAvailabilityAndPluginIdentityAreEnforced() {
        var registry = new ApplicationCapabilityProviderRuntimeRegistry();
        register(registry, provider("provider.orders", "orders", List.of()),
                tenant -> tenant.equals(TENANT),
                () -> new FakePlugin("wrong.plugin.id", "orders", new ArrayList<>(), false));
        var adapter = adapter(registry);
        var otherTenant = TenantId.generate();
        assertThat(registry.findAvailable(otherTenant).toCompletableFuture().join()).isEmpty();

        var command = activation(TENANT, UUID.randomUUID(),
                manifest(List.of(new CapabilityRequirement("orders", "^1.0.0"))));
        assertThatThrownBy(() -> adapter.activate(command).toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(SecurityException.class);
    }

    @Test
    void packageUpgradeReplacesProvidersAndStaleDeactivationCannotStopNewRevision() {
        List<String> lifecycle = new ArrayList<>();
        var registry = new ApplicationCapabilityProviderRuntimeRegistry();
        register(registry, provider("provider.orders", "orders", List.of()), tenant -> true,
                () -> new FakePlugin("provider.orders", "orders", lifecycle, false));
        var adapter = adapter(registry);
        var installation = UUID.randomUUID();
        var versionOne = activation(TENANT, installation,
                manifest(List.of(new CapabilityRequirement("orders", "^1.0.0"))));
        adapter.activate(versionOne).toCompletableFuture().join();

        var versionTwoManifest = new ApplicationManifest(APP, "2.0.0", "test-publisher", "Orders",
                List.of(new CapabilityRequirement("orders", "^1.0.0")), List.of(), Set.of(), List.of());
        var versionTwo = activation(TENANT, installation, versionTwoManifest, "2.0.0");
        adapter.activate(versionTwo).toCompletableFuture().join();

        adapter.deactivate(deactivation(versionOne)).toCompletableFuture().join();
        assertThat(lifecycle).containsExactly("start:provider.orders", "stop:provider.orders",
                "start:provider.orders");
        assertThat(adapter.health(TENANT, installation))
                .isEqualTo(RuntimeApplicationHostCompositionAdapter.RuntimeHealth.READY);

        adapter.deactivate(deactivation(versionTwo)).toCompletableFuture().join();
        assertThat(lifecycle).containsExactly("start:provider.orders", "stop:provider.orders",
                "start:provider.orders", "stop:provider.orders");
    }

    @Test
    void activationIsIdempotentAndInstancesAreScopedPerTenantInstallation() {
        AtomicInteger created = new AtomicInteger();
        var registry = new ApplicationCapabilityProviderRuntimeRegistry();
        register(registry, provider("provider.orders", "orders", List.of()), tenant -> true,
                () -> {
                    created.incrementAndGet();
                    return new FakePlugin("provider.orders", "orders", new ArrayList<>(), false);
                });
        var adapter = adapter(registry);
        var installation = UUID.randomUUID();
        var first = activation(TENANT, installation,
                manifest(List.of(new CapabilityRequirement("orders", "^1.0.0"))));
        adapter.activate(first).toCompletableFuture().join();
        adapter.activate(first).toCompletableFuture().join();
        assertThat(created).hasValue(1);

        var secondTenant = TenantId.generate();
        var second = activation(secondTenant, installation,
                manifest(List.of(new CapabilityRequirement("orders", "^1.0.0"))));
        adapter.activate(second).toCompletableFuture().join();
        assertThat(created).hasValue(2);
        assertThat(adapter.health(TENANT, installation))
                .isEqualTo(RuntimeApplicationHostCompositionAdapter.RuntimeHealth.READY);
        assertThat(adapter.health(secondTenant, installation))
                .isEqualTo(RuntimeApplicationHostCompositionAdapter.RuntimeHealth.READY);
    }

    private static RuntimeApplicationHostCompositionAdapter adapter(
            ApplicationCapabilityProviderRuntimeRegistry registry) {
        var resolver = new ResolvingApplicationCapabilityCompatibilityAdapter(registry,
                new ApplicationCapabilityResolver());
        return new RuntimeApplicationHostCompositionAdapter(resolver, registry,
                new DefaultApplicationPluginContextFactory());
    }

    private static void register(ApplicationCapabilityProviderRuntimeRegistry registry,
            CapabilityProvider descriptor, Predicate<TenantId> availability,
            Supplier<? extends NumouPlugin> factory) {
        registry.register(new ApplicationCapabilityProviderRegistration(descriptor, availability, factory));
    }

    private static CapabilityProvider provider(String providerId, String capability,
            List<CapabilityRequirement> dependencies) {
        return new CapabilityProvider(providerId, capability, "1.0.0", dependencies);
    }

    private static ApplicationManifest manifest(List<CapabilityRequirement> required) {
        return new ApplicationManifest(APP, "1.0.0", "test-publisher", "Orders",
                required, List.of(), Set.of(), List.of());
    }

    private static ApplicationHostCompositionPort.ActivationCommand activation(
            TenantId tenant, UUID installation, ApplicationManifest manifest) {
        return activation(tenant, installation, manifest, "1.0.0");
    }

    private static ApplicationHostCompositionPort.ActivationCommand activation(
            TenantId tenant, UUID installation, ApplicationManifest manifest, String version) {
        var pkg = new ValidatedApplicationPackage(manifest, DIGEST);
        return new ApplicationHostCompositionPort.ActivationCommand(tenant, installation, APP,
                version, DIGEST, UUID.randomUUID(), "test-operator", "test-correlation", pkg);
    }

    private static ApplicationHostCompositionPort.DeactivationCommand deactivation(
            ApplicationHostCompositionPort.ActivationCommand command) {
        return new ApplicationHostCompositionPort.DeactivationCommand(command.tenantId(),
                command.installationId(), command.applicationId(), command.packageVersion(),
                command.packageDigest(), UUID.randomUUID(), command.requestedBy(),
                command.correlationId(), "TEST");
    }

    private static final class FakePlugin implements NumouPlugin {
        private final String id;
        private final String capability;
        private final List<String> lifecycle;
        private final boolean failStart;
        private PluginState state = PluginState.LOADED;

        private FakePlugin(String id, String capability, List<String> lifecycle, boolean failStart) {
            this.id = id;
            this.capability = capability;
            this.lifecycle = lifecycle;
            this.failStart = failStart;
        }

        @Override public String id() { return id; }
        @Override public PluginManifest manifest() {
            return new PluginManifest(id, id, "1.0.0", "", "test",
                    Set.of(Capability.of(capability, "1.0.0")), List.of());
        }
        @Override public PluginState state() { return state; }
        @Override public void initialize(PluginContext context) { state = PluginState.INITIALIZED; }
        @Override public void start() {
            lifecycle.add("start:" + id);
            if (failStart) {
                state = PluginState.FAILED;
                throw new IllegalStateException("start failure");
            }
            state = PluginState.ACTIVE;
        }
        @Override public void stop() {
            lifecycle.add("stop:" + id);
            state = PluginState.STOPPED;
        }
    }
}
