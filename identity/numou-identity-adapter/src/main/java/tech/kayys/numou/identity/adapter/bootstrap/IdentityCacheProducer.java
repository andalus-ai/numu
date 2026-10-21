package tech.kayys.numou.identity.adapter.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.cache.CachePolicy;
import tech.kayys.numou.cache.adapter.CaffeineCache;
import tech.kayys.numou.identity.application.dto.PatResponse;
import tech.kayys.numou.identity.application.service.PersonalAccessTokenService;
import tech.kayys.numou.identity.application.service.ScopedRoleAssignmentService;

import java.time.Duration;
import java.util.Set;

/**
 * Wires the framework {@link Cache} port (Caffeine L1) into the identity use cases.
 *
 * <p>Both caches sit on the hot authorization path, so TTLs are deliberately short:
 * the TTL is the upper bound on how long a revoked grant or a revoked token keeps
 * working after the revocation (local revocations also evict explicitly).
 */
@ApplicationScoped
public class IdentityCacheProducer {

    /** Effective-permission resolutions for the gRPC CheckPermission(s) hot path. */
    @Produces
    @ApplicationScoped
    public Cache<Set<String>> effectivePermissionCache() {
        return new CaffeineCache<>(
                ScopedRoleAssignmentService.PERMISSION_CACHE_NAMESPACE,
                new CachePolicy(Duration.ofSeconds(60), Duration.ZERO, 50_000)
        );
    }

    /** Validated PAT hashes so repeated calls with one token skip the database entirely. */
    @Produces
    @ApplicationScoped
    public Cache<PatResponse> patValidationCache() {
        return new CaffeineCache<>(
                PersonalAccessTokenService.VALIDATION_CACHE_NAMESPACE,
                new CachePolicy(Duration.ofSeconds(60), Duration.ZERO, 50_000)
        );
    }
}
