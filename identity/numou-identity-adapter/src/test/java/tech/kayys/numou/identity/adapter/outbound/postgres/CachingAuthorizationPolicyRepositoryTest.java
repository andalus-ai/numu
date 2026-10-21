package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.cache.CachePolicy;
import tech.kayys.numou.cache.adapter.CaffeineCache;
import tech.kayys.numou.identity.application.port.AuthorizationPolicyRepository;
import tech.kayys.numou.identity.application.security.abac.AttributeNamespace;
import tech.kayys.numou.identity.application.security.abac.AttributeReference;
import tech.kayys.numou.identity.application.security.abac.AuthorizationPolicy;
import tech.kayys.numou.identity.application.security.abac.Comparison;
import tech.kayys.numou.identity.application.security.abac.ComparisonOperator;
import tech.kayys.numou.identity.application.security.abac.PolicyEffect;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CachingAuthorizationPolicyRepository Tests")
class CachingAuthorizationPolicyRepositoryTest {

    private static final String TENANT_ID = UUID.randomUUID().toString();
    private static final String PERMISSION = "order:approve";

    private AtomicInteger delegateInvocationCount;
    private AuthorizationPolicyRepository delegate;
    private CachingAuthorizationPolicyRepository repository;

    @BeforeEach
    void setUp() {
        delegateInvocationCount = new AtomicInteger(0);
        delegate = (tenantId, permission) -> {
            delegateInvocationCount.incrementAndGet();
            if (tenantId == null || tenantId.isBlank() || permission == null || permission.isBlank()) {
                return Uni.createFrom().item(List.of());
            }
            var policy = new AuthorizationPolicy(
                    UUID.randomUUID(),
                    tenantId,
                    permission,
                    PolicyEffect.ALLOW,
                    new Comparison(
                            new AttributeReference(AttributeNamespace.SUBJECT, "role"),
                            ComparisonOperator.EQUALS,
                            "admin"
                    ),
                    true,
                    1L
            );
            List<AuthorizationPolicy> list = List.of(policy);
            return Uni.createFrom().item(list);
        };

        var cache = new CaffeineCache<List<AuthorizationPolicy>>(
                CachingAuthorizationPolicyRepository.NAMESPACE,
                new CachePolicy(Duration.ofSeconds(30), Duration.ofSeconds(5), 1_000)
        );

        repository = new CachingAuthorizationPolicyRepository(delegate, cache);
    }

    @Test
    @DisplayName("Cache miss queries delegate and subsequent call hits cache")
    void cacheMissThenHit() {
        var first = repository.findActive(TENANT_ID, PERMISSION).await().indefinitely();
        assertNotNull(first);
        assertEquals(1, first.size());
        assertEquals(1, delegateInvocationCount.get(), "First call must hit delegate");

        var second = repository.findActive(TENANT_ID, PERMISSION).await().indefinitely();
        assertNotNull(second);
        assertEquals(1, delegateInvocationCount.get(), "Second call must be served from cache");
    }

    @Test
    @DisplayName("Invalidation evicts cached item and triggers reload")
    void invalidateSingleKey() {
        repository.findActive(TENANT_ID, PERMISSION).await().indefinitely();
        assertEquals(1, delegateInvocationCount.get());

        repository.invalidate(TENANT_ID, PERMISSION).await().indefinitely();

        repository.findActive(TENANT_ID, PERMISSION).await().indefinitely();
        assertEquals(2, delegateInvocationCount.get(), "Call after invalidation must hit delegate again");
    }

    @Test
    @DisplayName("Invalidate tenant evicts all entries for that tenant")
    void invalidateTenant() {
        String perm2 = "order:cancel";
        repository.findActive(TENANT_ID, PERMISSION).await().indefinitely();
        repository.findActive(TENANT_ID, perm2).await().indefinitely();
        assertEquals(2, delegateInvocationCount.get());

        repository.invalidateTenant(TENANT_ID).await().indefinitely();

        repository.findActive(TENANT_ID, PERMISSION).await().indefinitely();
        assertEquals(3, delegateInvocationCount.get(), "Should reload after tenant invalidation");
    }

    @Test
    @DisplayName("Blank or null parameters bypass cache and directly invoke delegate")
    void nullOrBlankBypassesCache() {
        repository.findActive(null, PERMISSION).await().indefinitely();
        assertEquals(1, delegateInvocationCount.get());

        repository.findActive("", PERMISSION).await().indefinitely();
        assertEquals(2, delegateInvocationCount.get());

        repository.findActive(TENANT_ID, null).await().indefinitely();
        assertEquals(3, delegateInvocationCount.get());
    }
}
