package tech.kayys.syirkah.security.application.tenant;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.authentication.TenantResolutionException;
import tech.kayys.syirkah.security.spi.port.TenantMembershipResolver;
import tech.kayys.syirkah.security.spi.port.TenantResolutionRequest;
import tech.kayys.syirkah.security.spi.port.TenantResolver;

import java.util.Objects;

/**
 * Default implementation of {@link TenantResolver} (security02.md §P3-18).
 *
 * <p>Validates the requested tenant against the authenticated principal.
 * Uses {@link TenantMembershipResolver} when present for authoritative store-backed
 * verification; otherwise checks the principal's tenant memberships.
 */
@ApplicationScoped
public class DefaultTenantResolver implements TenantResolver {

    private final TenantMembershipResolver membershipResolver;

    public DefaultTenantResolver() {
        this.membershipResolver = null;
    }

    public DefaultTenantResolver(TenantMembershipResolver membershipResolver) {
        this.membershipResolver = membershipResolver;
    }

    @Inject
    public DefaultTenantResolver(Instance<TenantMembershipResolver> membershipResolvers) {
        this.membershipResolver = (membershipResolvers != null && membershipResolvers.isResolvable())
                ? membershipResolvers.get()
                : null;
    }

    @Override
    public Uni<TenantId> resolve(TenantResolutionRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(request.principal(), "request.principal() cannot be null");

        if (request.requestedTenant() == null || request.requestedTenant().isBlank()) {
            return Uni.createFrom().failure(new TenantResolutionException("tenant.required"));
        }

        final TenantId tenant;
        try {
            tenant = TenantId.of(request.requestedTenant().trim());
        } catch (RuntimeException e) {
            return Uni.createFrom().failure(new TenantResolutionException("tenant.invalid", e));
        }

        if (membershipResolver != null) {
            return membershipResolver.isMember(request.principal(), tenant)
                    .chain(isMember -> {
                        if (!Boolean.TRUE.equals(isMember)) {
                            return Uni.createFrom().failure(new TenantResolutionException("tenant.access"));
                        }
                        return Uni.createFrom().item(tenant);
                    });
        }

        if (!request.principal().isMemberOf(tenant)) {
            return Uni.createFrom().failure(new TenantResolutionException("tenant.access"));
        }

        return Uni.createFrom().item(tenant);
    }
}
