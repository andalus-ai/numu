package tech.kayys.numou.identity.adapter.outbound.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class IdentityJwtKeyIdTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    SmallRyeJwtTokenAdapter userTokenIssuer;

    @Inject
    TenantJwtTokenIssuerAdapter tenantTokenIssuer;

    @Inject
    SmallRyeOAuthAccessTokenIssuer oauthTokenIssuer;

    @Inject
    JWTParser jwtParser;

    @Test
    void everyIdentityTokenIssuerStampsKidAndVerifiesAgainstTheOverlappingJwks() throws Exception {
        UUID principalId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        assertKidAndVerify(userTokenIssuer.issueToken(principalId, "USER", "Test User"));
        assertKidAndVerify(tenantTokenIssuer.issueTenantSelectionToken(principalId, "Test User"));
        assertKidAndVerify(tenantTokenIssuer.issueTenantAccessToken(principalId, "Test User", tenantId));
        assertKidAndVerify(oauthTokenIssuer.issueClientAccessToken(
                "test-client", tenantId, applicationId, Set.of("workspace.read"), 1L));
    }


    @Test
    void tenantSelectionAndAccessTokensHaveDistinctClaimsAndBoundedLifetimes() throws Exception {
        UUID principalId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        JWTCallerPrincipal selection = jwtParser.parse(
                tenantTokenIssuer.issueTenantSelectionToken(principalId, "Test User"));
        JWTCallerPrincipal access = jwtParser.parse(
                tenantTokenIssuer.issueTenantAccessToken(principalId, "Test User", tenantId));

        assertEquals("tenant_selection", selection.getClaim("token_use"));
        assertEquals("USER", selection.getClaim("principalType"));
        assertNull(selection.getClaim("tenant_id"));
        assertNull(selection.getClaim("numou_tenant_id"));
        assertTrue(selection.getAudience().contains("numou-identity-tenant-selection"));
        assertTrue(selection.getExpirationTime() > selection.getIssuedAtTime());
        assertTrue(selection.getExpirationTime() - selection.getIssuedAtTime() <= 300);

        assertEquals("access", access.getClaim("token_use"));
        assertEquals("USER", access.getClaim("principalType"));
        assertEquals(tenantId.toString(), access.getClaim("tenant_id"));
        assertEquals(tenantId.toString(), access.getClaim("numou_tenant_id"));
        assertTrue(access.getAudience().contains("numou-identity-api"));
        assertTrue(access.getExpirationTime() > access.getIssuedAtTime());
        assertTrue(access.getExpirationTime() - access.getIssuedAtTime() <= 900);
    }

    private void assertKidAndVerify(String token) throws Exception {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "token must be a compact JWS");
        JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertEquals("test-identity-key", header.path("kid").asText());
        JWTCallerPrincipal principal = jwtParser.parse(token);
        assertNotNull(principal.getSubject());
    }
}
