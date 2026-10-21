package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.cache.CacheKey;
import tech.kayys.numou.cache.CachePolicy;
import tech.kayys.numou.cache.adapter.CaffeineCache;
import tech.kayys.numou.identity.application.port.AuthorizationPolicyRepository;
import tech.kayys.numou.identity.application.security.abac.AuthorizationPolicy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Read-through cache decorator for {@link AuthorizationPolicyRepository}.
 *
 * <p>ABAC policies are tenant-scoped, per-permission lists that change rarely
 * (administrative action) but are queried on <em>every</em> authorization check.
 * This decorator caches the policy list in-process for 30 seconds with a 5-second
 * initial-fill window, reducing Postgres load to near-zero under steady traffic.
 *
 * <h3>Cache key</h3>
 * {@code identity.abac.policies:<tenantId>:<permission>}
 *
 * <h3>Invalidation</h3>
 * When a policy is written (created/updated/deactivated) the application service
 * must call {@link #invalidate(String, String)} so the next request re-fetches from
 * Postgres.  Tenant-wide invalidation ({@link #invalidateTenant(String)}) is
 * provided for bulk operations.
 *
 * <p>TTL-based eviction ensures stale policies are never served beyond 30 seconds
 * even if the write path forgets to invalidate.
 */
@ApplicationScoped
@Priority(10)
public class CachingAuthorizationPolicyRepository implements AuthorizationPolicyRepository {

    static final String NAMESPACE = "identity.abac.policies";

    private final AuthorizationPolicyRepository delegate;
    private final Cache<List<AuthorizationPolicy>> cache;

    /** CDI production constructor — delegate is the Postgres implementation. */
    @Inject
    public CachingAuthorizationPolicyRepository(
            PostgresAuthorizationPolicyRepository delegate) {
        this(delegate, new CaffeineCache<>(NAMESPACE,
                new CachePolicy(Duration.ofSeconds(30), Duration.ofSeconds(5), 10_000)));
    }

    /** Testable constructor allowing an injected cache stub. */
    public CachingAuthorizationPolicyRepository(
            AuthorizationPolicyRepository delegate,
            Cache<List<AuthorizationPolicy>> cache) {
        this.delegate = delegate;
        this.cache    = cache;
    }

    @Override
    public Uni<List<AuthorizationPolicy>> findActive(String tenantId, String permission) {
        if (tenantId == null || tenantId.isBlank() || permission == null || permission.isBlank()) {
            return delegate.findActive(tenantId, permission);
        }
        var key = policyKey(tenantId, permission);
        return cache.getOrLoad(key, () -> delegate.findActive(tenantId, permission));
    }

    /**
     * Invalidates the cached list for a single {@code (tenantId, permission)} pair.
     * Call after any write to {@code identity_authorization_policy} for this pair.
     */
    public Uni<Void> invalidate(String tenantId, String permission) {
        return cache.invalidate(policyKey(tenantId, permission));
    }

    /**
     * Invalidates all cached policy lists for a tenant.
     * Call after bulk policy imports or tenant deletion.
     */
    public Uni<Void> invalidateTenant(String tenantId) {
        try {
            UUID tenantUuid = UUID.fromString(tenantId);
            return cache.invalidateTenant(tenantUuid);
        } catch (IllegalArgumentException ignored) {
            return Uni.createFrom().voidItem();
        }
    }

    private static CacheKey policyKey(String tenantId, String permission) {
        UUID tenantUuid;
        try {
            tenantUuid = UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            tenantUuid = new UUID(0L, 0L);
        }
        return CacheKey.of(NAMESPACE, tenantUuid, permission);
    }
}
