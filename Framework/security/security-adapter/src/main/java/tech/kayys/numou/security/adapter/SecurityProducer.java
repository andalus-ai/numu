package tech.kayys.numou.security.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.cache.CachePolicy;
import tech.kayys.numou.cache.adapter.CaffeineCache;
import tech.kayys.numou.security.application.authz.AuthorizationService;
import tech.kayys.numou.security.application.authz.CachingGrantResolver;
import tech.kayys.numou.security.application.authz.GrantResolver;
import tech.kayys.numou.security.application.authz.RoleGrantResolver;
import tech.kayys.numou.security.application.authz.RolePermissionMatrix;
import tech.kayys.numou.security.application.problem.ProblemMapper;
import tech.kayys.numou.security.application.ratelimit.TokenBucketRateLimiter;
import tech.kayys.numou.security.spi.port.AuditPort;

import java.time.Duration;

/** Wires the pure security services into CDI. NOT COMPILED in the authoring sandbox. */
@ApplicationScoped
public class SecurityProducer {

    @Inject
    SecurityConfig config;

    @Produces
    @ApplicationScoped
    TokenBucketRateLimiter rateLimiter() {
        final var c = config.ratelimit();
        return new TokenBucketRateLimiter(c.capacity(), c.refillPerSecond(), c.maxKeys());
    }

    /** Role matrix for the restaurant suite; replace the bean to use another matrix or a database-backed GrantResolver. */
    @Produces
    @ApplicationScoped
    RolePermissionMatrix roleMatrix() {
        return RolePermissionMatrix.restaurantDefaults();
    }

    @Produces
    @ApplicationScoped
    GrantResolver grantResolver(RolePermissionMatrix matrix) {
        // Short TTL: a revoked role stays effective for at most 30 seconds.
        final var cache = new CaffeineCache<java.util.Set<tech.kayys.numou.security.domain.authorization.Permission>>(
                CachingGrantResolver.NAMESPACE, new CachePolicy(Duration.ofSeconds(30), Duration.ZERO, 50_000));
        return new CachingGrantResolver(new RoleGrantResolver(matrix), cache);
    }

    @Produces
    @ApplicationScoped
    AuthorizationService authorizationService(GrantResolver grants, AuditPort audit) {
        return new AuthorizationService(grants, audit);
    }

    @Produces
    @ApplicationScoped
    ProblemMapper problemMapper(@Any Instance<ProblemRuleContributor> contributors) {
        final var mapper = ProblemMapper.defaults();
        contributors.forEach(c -> c.contribute(mapper));
        return mapper;
    }
}
