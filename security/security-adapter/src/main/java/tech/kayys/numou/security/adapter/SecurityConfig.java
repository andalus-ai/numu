package tech.kayys.numou.security.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * Runtime security and rate-limit configuration.
 *
 * <p>Use MicroProfile {@code @ConfigProperty} injection rather than a SmallRye
 * {@code @ConfigMapping} synthetic bean: this adapter is consumed by standalone
 * Quarkus applications, and its configuration must remain available when the
 * adapter is resolved from a dependency JAR.
 */
@ApplicationScoped
public class SecurityConfig {

    @ConfigProperty(name = "numou.security.tenant-claim", defaultValue = "numou_tenant_id")
    String tenantClaim;

    @ConfigProperty(name = "numou.security.tenants-claim", defaultValue = "numou_tenants")
    String tenantsClaim;

    @ConfigProperty(name = "numou.security.user-id-claim", defaultValue = "numou_user_id")
    String userIdClaim;

    @ConfigProperty(name = "numou.security.trust-forwarded-for", defaultValue = "false")
    boolean trustForwardedFor;

    @ConfigProperty(name = "numou.security.protected-prefixes", defaultValue = "/api/")
    List<String> protectedPrefixes;

    @ConfigProperty(name = "numou.ratelimit.enabled", defaultValue = "true")
    boolean rateLimitEnabled;

    @ConfigProperty(name = "numou.ratelimit.capacity", defaultValue = "60")
    long rateLimitCapacity;

    @ConfigProperty(name = "numou.ratelimit.refill-per-second", defaultValue = "20")
    double rateLimitRefillPerSecond;

    @ConfigProperty(name = "numou.ratelimit.max-keys", defaultValue = "100000")
    int rateLimitMaxKeys;

    public Security security() {
        return new Security(tenantClaim, tenantsClaim, userIdClaim, trustForwardedFor, protectedPrefixes);
    }

    public Ratelimit ratelimit() {
        return new Ratelimit(rateLimitEnabled, rateLimitCapacity, rateLimitRefillPerSecond, rateLimitMaxKeys);
    }

    public record Security(
            String tenantClaim,
            String tenantsClaim,
            String userIdClaim,
            boolean trustForwardedFor,
            List<String> protectedPrefixes) {
    }

    public record Ratelimit(
            boolean enabled,
            long capacity,
            double refillPerSecond,
            int maxKeys) {
    }
}
