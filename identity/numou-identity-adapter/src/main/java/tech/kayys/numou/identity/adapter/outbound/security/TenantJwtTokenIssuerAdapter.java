package tech.kayys.numou.identity.adapter.outbound.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.identity.application.port.TenantTokenIssuerPort;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Signs narrowly purposed human tenant-selection and tenant-bound access credentials. */
@ApplicationScoped
public class TenantJwtTokenIssuerAdapter implements TenantTokenIssuerPort {
    private static final String PRINCIPAL_TYPE = "USER";
    private static final String TENANT_SELECTION_USE = "tenant_selection";
    private static final String ACCESS_USE = "access";

    @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
    String issuer;

    @ConfigProperty(name = "numou.identity.tenant-selection.audience",
            defaultValue = "numou-identity-tenant-selection")
    String selectionAudience;

    @ConfigProperty(name = "numou.oauth.access-token.audience")
    String accessAudience;

    @ConfigProperty(name = "numou.identity.tenant-selection.lifespan-seconds", defaultValue = "300")
    long selectionLifetimeSeconds;

    @ConfigProperty(name = "numou.identity.tenant-access.lifespan-seconds", defaultValue = "900")
    long accessLifetimeSeconds;

    @Override
    public String issueTenantSelectionToken(UUID principalId, String displayName) {
        requirePrincipal(principalId, displayName);
        if (selectionLifetimeSeconds < 60 || selectionLifetimeSeconds > 600) {
            throw new IllegalStateException("Tenant selection token lifetime must be between 60 and 600 seconds");
        }
        return Jwt.issuer(issuer)
                .subject(principalId.toString())
                .upn(displayName)
                .audience(selectionAudience)
                .claim("principalType", PRINCIPAL_TYPE)
                .claim("token_use", TENANT_SELECTION_USE)
                .groups(Set.of())
                .expiresIn(Duration.ofSeconds(selectionLifetimeSeconds))
                .sign();
    }

    @Override
    public String issueTenantAccessToken(UUID principalId, String displayName, UUID tenantId) {
        requirePrincipal(principalId, displayName);
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (accessLifetimeSeconds < 60 || accessLifetimeSeconds > 900) {
            throw new IllegalStateException("Tenant access token lifetime must be between 60 and 900 seconds");
        }
        String canonicalTenant = tenantId.toString();
        return Jwt.issuer(issuer)
                .subject(principalId.toString())
                .upn(displayName)
                .audience(accessAudience)
                .claim("principalType", PRINCIPAL_TYPE)
                .claim("tenant_id", canonicalTenant)
                // Temporary compatibility alias for the existing identity guard.
                .claim("numou_tenant_id", canonicalTenant)
                .claim("token_use", ACCESS_USE)
                .claim("jti", UUID.randomUUID().toString())
                .groups(Set.of())
                .expiresIn(Duration.ofSeconds(accessLifetimeSeconds))
                .sign();
    }

    private static void requirePrincipal(UUID principalId, String displayName) {
        if (principalId == null || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("A verified user principal is required");
        }
    }
}
