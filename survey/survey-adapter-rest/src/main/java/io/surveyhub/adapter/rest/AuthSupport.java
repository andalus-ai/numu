package io.surveyhub.adapter.rest;

import io.surveyhub.application.Role;
import io.surveyhub.application.TenantContext;
import io.surveyhub.domain.id.TenantId;
import io.surveyhub.security.AuthenticationException;
import io.surveyhub.security.JwtVerifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Turns request credentials into a {@link TenantContext}.
 * <ul>
 *   <li>{@code survey.auth.mode=jwt} (default, production): a signed bearer token carries subject, tenant and roles.
 *       Startup fails when no secret of at least 32 bytes is configured, so the API can never boot open.</li>
 *   <li>{@code survey.auth.mode=headers} (dev only): X-Tenant-Id, X-Actor-Id and optional X-Roles are trusted as sent.</li>
 * </ul>
 */
@Singleton
public class AuthSupport {
    private final boolean jwt;
    private final JwtVerifier verifier;

    @Inject
    public AuthSupport(@ConfigProperty(name = "survey.auth.mode", defaultValue = "jwt") String mode,
                       @ConfigProperty(name = "survey.auth.jwt-secret") Optional<String> secret,
                       @ConfigProperty(name = "survey.auth.issuer") Optional<String> issuer,
                       @ConfigProperty(name = "survey.auth.audience") Optional<String> audience) {
        this.jwt = !"headers".equalsIgnoreCase(mode);
        if (jwt) {
            byte[] key = secret.orElse("").getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) throw new IllegalStateException(
                    "survey.auth.jwt-secret (SURVEY_JWT_SECRET) must be at least 32 bytes when survey.auth.mode=jwt");
            this.verifier = new JwtVerifier(key, issuer.orElse(null), audience.orElse(null), Clock.systemUTC(), Duration.ofSeconds(30));
        } else {
            this.verifier = null;
        }
    }

    public TenantContext authenticate(ContainerRequestContext request) {
        if (jwt) {
            String h = request.getHeaderString("Authorization");
            if (h == null || !h.regionMatches(true, 0, "Bearer ", 0, 7)) throw new AuthenticationException("Bearer token required");
            return verifier.verify(h.substring(7).trim());
        }
        String tenant = request.getHeaderString("X-Tenant-Id");
        if (tenant == null || tenant.isBlank()) throw new AuthenticationException("X-Tenant-Id header required");
        Set<Role> roles = EnumSet.noneOf(Role.class);
        String raw = request.getHeaderString("X-Roles");
        if (raw == null || raw.isBlank()) roles.add(Role.OWNER);
        else for (String r : raw.split(",")) {
            try { roles.add(Role.valueOf(r.trim().toUpperCase())); } catch (IllegalArgumentException ignored) { /* unknown */ }
        }
        return new TenantContext(new TenantId(tenant.trim()), request.getHeaderString("X-Actor-Id"), roles);
    }
}
