package tech.kayys.syirkah.security.adapter;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/** {@code syirkah.security.*} / {@code syirkah.ratelimit.*} configuration. */
@ConfigMapping(prefix = "syirkah")
public interface SecurityConfig {

    Security security();

    Ratelimit ratelimit();

    interface Security {
        /** JWT claim holding the active tenant id. */
        @WithDefault("syirkah_tenant_id")
        String tenantClaim();

        /** JWT claim holding every tenant the user belongs to (array of UUIDs). */
        @WithDefault("syirkah_tenants")
        String tenantsClaim();

        /** JWT claim holding the principal's UUID. */
        @WithDefault("syirkah_user_id")
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
