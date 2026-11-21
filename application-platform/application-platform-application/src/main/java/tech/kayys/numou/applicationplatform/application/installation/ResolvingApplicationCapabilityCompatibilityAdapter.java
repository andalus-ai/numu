package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolution;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Adapts the existing manifest capability resolver to tenant-specific host availability.
 * It resolves provider dependencies and SemVer ranges without loading application code.
 */
public final class ResolvingApplicationCapabilityCompatibilityAdapter
        implements ApplicationCapabilityCompatibilityPort {
    private final ApplicationCapabilityProviderCatalogPort providers;
    private final ApplicationCapabilityResolver resolver;

    public ResolvingApplicationCapabilityCompatibilityAdapter(
            ApplicationCapabilityProviderCatalogPort providers,
            ApplicationCapabilityResolver resolver) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public CompletionStage<CapabilityResolution> resolve(
            TenantId tenantId, ValidatedApplicationPackage applicationPackage) {
        if (tenantId == null || applicationPackage == null)
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("tenant and validated application package are required"));
        return providers.findAvailable(tenantId).thenApply(available ->
                resolver.resolve(applicationPackage.manifest(), available));
    }

    @Override
    public CompletionStage<Void> assertCompatible(
            TenantId tenantId, ValidatedApplicationPackage applicationPackage) {
        return resolve(tenantId, applicationPackage).thenApply(ignored -> null);
    }
}
