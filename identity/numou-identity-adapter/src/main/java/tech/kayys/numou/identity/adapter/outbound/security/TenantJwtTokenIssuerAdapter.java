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
    private static final long SELECTION_LIFETIME_SECONDS = 300;
    private static final long ACCESS_LIFETIME_SECONDS = 900;

    @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
    String issuer;

    @ConfigProperty(name = "numou.identity.tenant-selection.audience",
            defaultValue = "numou-identity-tenant-selection")
    String selectionAudience;

    @ConfigProperty(name = "numou.oauth.access-token.audience")
    String accessAudience;

    @ConfigProperty(name = "numou.identity.jwt.signing-key-id")
    String signingKeyId;

    @Override
    public String issueTenantSelectionToken(UUID principalId, String displayName) {
        requirePrincipal(principalId, displayName);
        return Jwt.issuer(issuer)
                .subject(principalId.toString())
                .upn(displayName)
                .audience(selectionAudience)
                .claim("principalType", PRINCIPAL_TYPE)
                .claim("token_use", TENANT_SELECTION_USE)
                .groups(Set.of())
                .expiresIn(Duration.ofSeconds(SELECTION_LIFETIME_SECONDS))
                .jws().keyId(requireSigningKeyId()).sign();
    }

    @Override
    public String issueTenantAccessToken(UUID principalId, String displayName, UUID tenantId) {
        requirePrincipal(principalId, displayName);
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
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
                .expiresIn(Duration.ofSeconds(ACCESS_LIFETIME_SECONDS))
                .jws().keyId(requireSigningKeyId()).sign();
    }

    private String requireSigningKeyId() {
        if (signingKeyId == null || signingKeyId.isBlank() || signingKeyId.length() > 128) {
            throw new IllegalStateException("Identity JWT signing key ID must be configured");
        }
        return signingKeyId;
    }

    private static void requirePrincipal(UUID principalId, String displayName) {
        if (principalId == null || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("A verified user principal is required");
        }
    }
}
