package tech.kayys.numou.security.adapter;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/** {@code numou.security.*} / {@code numou.ratelimit.*} configuration. */
@ConfigMapping(prefix = "numou")
public interface SecurityConfig {

    Security security();

    Ratelimit ratelimit();

    interface Security {
        /** JWT claim holding the active tenant id. */
        @WithDefault("numou_tenant_id")
        String tenantClaim();

        /** JWT claim holding every tenant the user belongs to (array of UUIDs). */
        @WithDefault("numou_tenants")
        String tenantsClaim();

        /** JWT claim holding the principal's UUID. */
        @WithDefault("numou_user_id")
        String userIdClaim();

        /** Only set true behind a proxy that overwrites X-Forwarded-For; otherwise clients could spoof their IP. */
        @WithDefault("false")
        boolean trustForwardedFor();

        /** Path prefixes protected by tenant resolution. */
        @WithDefault("/api/")
        List<String> protectedPrefixes();
    }

    interface Ratelimit {
        @WithDefault("true")
        boolean enabled();

        /** Burst size per (tenant, principal). */
        @WithDefault("60")
        long capacity();

        @WithDefault("20")
        double refillPerSecond();

        @WithDefault("100000")
        int maxKeys();
    }
}
