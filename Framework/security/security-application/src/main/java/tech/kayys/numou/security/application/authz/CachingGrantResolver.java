package tech.kayys.syirkah.security.application.authz;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.cache.Cache;
import tech.kayys.syirkah.cache.CacheKey;
import tech.kayys.syirkah.security.application.context.SecurityContext;
import tech.kayys.syirkah.security.domain.authorization.Permission;

import java.util.Objects;
import java.util.Set;

/**
 * Caches grants per (tenant, principal) so a database-backed resolver is not hit
 * on every request. Keep the TTL short: a revoked role stays effective for at
 * most one TTL. Role changes should also call {@link Cache#invalidateTenant}.
 */
public final class CachingGrantResolver implements GrantResolver {

    public static final String NAMESPACE = "security.grants";

    private final GrantResolver delegate;
    private final Cache<Set<Permission>> cache;

    public CachingGrantResolver(GrantResolver delegate, Cache<Set<Permission>> cache) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
    }

    @Override
    public Uni<Set<Permission>> grantsFor(SecurityContext context) {
        // Roles are part of the key: a principal whose token roles changed must not reuse old grants.
        final var rolesKey = String.join(",", context.roles().stream().sorted().toList());
        final var key = CacheKey.of(NAMESPACE, context.tenantId(),
                context.principal().id().value() + ":" + rolesKey);
        return cache.getOrLoad(key, () -> delegate.grantsFor(context));
    }
}
