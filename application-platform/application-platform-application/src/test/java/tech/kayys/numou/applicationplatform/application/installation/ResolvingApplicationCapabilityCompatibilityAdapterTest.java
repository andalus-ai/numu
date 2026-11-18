package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.CapabilityRequirement;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolutionException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvingApplicationCapabilityCompatibilityAdapterTest {
    private static final TenantId TENANT = TenantId.generate();
    private static final String DIGEST = "b".repeat(64);

    @Test
    void resolvesRequiredAndOptionalCapabilitiesAgainstTenantCatalog() {
        var adapter = adapter(List.of(
                provider("inventory.provider", "inventory", "1.4.0"),
                provider("billing.provider", "billing", "2.1.0")));
        var resolution = adapter.resolve(TENANT, packageWith(
                List.of(req("inventory", "^1.2.0")),
                List.of(req("analytics", "~1.0.0")))).toCompletableFuture().join();

        assertThat(resolution.providersByCapability()).containsOnlyKeys("inventory");
        assertThat(resolution.providersByCapability().get("inventory").providerId())
                .isEqualTo("inventory.provider");
        assertThat(resolution.unavailableOptionalCapabilities()).containsExactly("analytics");
        assertThat(resolution.initializationOrder()).extracting(CapabilityProvider::providerId)
                .containsExactly("inventory.provider");
    }

    @Test
    void failsClosedWhenRequiredCapabilityIsUnavailable() {
        var adapter = adapter(List.of(provider("billing.provider", "billing", "2.1.0")));
        assertThatThrownBy(() -> adapter.assertCompatible(TENANT,
                packageWith(List.of(req("inventory", "^1.0.0")), List.of()))
                .toCompletableFuture().join())
                .hasRootCauseInstanceOf(CapabilityResolutionException.class);
    }

    @Test
    void rejectsAmbiguousCompatibleProviders() {
        var adapter = adapter(List.of(
                provider("inventory.a", "inventory", "1.2.0"),
                provider("inventory.b", "inventory", "1.3.0")));
        assertThatThrownBy(() -> adapter.resolve(TENANT,
                packageWith(List.of(req("inventory", "^1.0.0")), List.of()))
                .toCompletableFuture().join())
                .hasRootCauseInstanceOf(CapabilityResolutionException.class);
    }

    @Test
    void providerCatalogIsAlwaysQueriedWithinTheRequestedTenant() {
        var observed = new java.util.concurrent.atomic.AtomicReference<TenantId>();
        var adapter = new ResolvingApplicationCapabilityCompatibilityAdapter(
                tenant -> {
                    observed.set(tenant);
                    return CompletableFuture.completedFuture(List.of(provider("inventory.provider", "inventory", "1.0.0")));
                }, new ApplicationCapabilityResolver());

        adapter.assertCompatible(TENANT, packageWith(List.of(req("inventory", "^1.0.0")), List.of()))
                .toCompletableFuture().join();
        assertThat(observed.get()).isEqualTo(TENANT);
    }

    private static ResolvingApplicationCapabilityCompatibilityAdapter adapter(List<CapabilityProvider> available) {
        return new ResolvingApplicationCapabilityCompatibilityAdapter(
                tenant -> CompletableFuture.completedFuture(available), new ApplicationCapabilityResolver());
    }

    private static CapabilityProvider provider(String providerId, String capabilityId, String version) {
        return new CapabilityProvider(providerId, capabilityId, version, List.of());
    }

    private static CapabilityRequirement req(String id, String range) {
        return new CapabilityRequirement(id, range);
    }

    private static ValidatedApplicationPackage packageWith(
            List<CapabilityRequirement> required, List<CapabilityRequirement> optional) {
        return new ValidatedApplicationPackage(new ApplicationManifest(
                "com.example.orders", "1.0.0", "test-publisher", "Orders",
                required, optional, Set.of(), List.of()), DIGEST);
    }
}
