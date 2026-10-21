package tech.kayys.numou.identity.adapter.outbound.idp.oidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.identity.adapter.outbound.idp.JacksonFederationSettingsParser;
import tech.kayys.numou.identity.application.dto.FederationAuthorizationRequest;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfigId;
import tech.kayys.numou.identity.domain.idp.IdentityProviderType;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The OIDC adapter's authorization step is pure local computation when the endpoints are explicit
 * (no discovery HTTP needed), so it is tested offline. Token exchange and JWKS verification need
 * a live IdP and are integration-test territory.
 */
class OidcFederationAdapterTest {

    private final OidcFederationAdapter adapter =
            new OidcFederationAdapter(new ObjectMapper(), new JacksonFederationSettingsParser(new ObjectMapper()));

    @Test
    void shouldBuildPkceAuthorizationUrlFromExplicitEndpoints() {
        IdentityProviderConfig config = IdentityProviderConfig.create(
                IdentityProviderConfigId.of(UUID.randomUUID()),
                "Corp Okta",
                IdentityProviderType.OIDC,
                """
                        {
                          "issuer": "https://idp.example",
                          "clientId": "numou",
                          "redirectUri": "https://app.example/api/v1/auth/federated/cb",
                          "scopes": ["openid", "email"],
                          "authorizationEndpoint": "https://idp.example/authorize",
                          "tokenEndpoint": "https://idp.example/token",
                          "jwksUri": "https://idp.example/jwks"
                        }
                        """,
                null,
                Instant.parse("2026-10-20T10:00:00Z")
        );

        var request = new FederationAuthorizationRequest("some-state", "some-nonce", "some-challenge");
        String url = adapter.authorizationUrl(config, request).await().indefinitely();

        assertTrue(url.startsWith("https://idp.example/authorize?"), url);
        assertTrue(url.contains("response_type=code"), url);
        assertTrue(url.contains("client_id=numou"), url);
        assertTrue(url.contains("scope=openid%20email"), url);
        assertTrue(url.contains("state=some-state"), url);
        assertTrue(url.contains("nonce=some-nonce"), url);
        assertTrue(url.contains("code_challenge=some-challenge"), url);
        assertTrue(url.contains("code_challenge_method=S256"), url);
    }

    @Test
    void shouldFailClosedWhenClientIdIsNotConfigured() {
        IdentityProviderConfig config = IdentityProviderConfig.create(
                IdentityProviderConfigId.of(UUID.randomUUID()),
                "Broken",
                IdentityProviderType.OIDC,
                "{\"redirectUri\": \"https://app.example/cb\"}",
                null,
                Instant.parse("2026-10-20T10:00:00Z")
        );

        var failure = assertThrows(ApplicationErrorException.class, () ->
                adapter.authorizationUrl(config, new FederationAuthorizationRequest("s", "n", "c"))
                        .await().indefinitely());
        assertEquals("FEDERATION_MISCONFIGURED", failure.error().code());
    }

    @Test
    void shouldRefuseNonHttpEndpoints() {
        IdentityProviderConfig config = IdentityProviderConfig.create(
                IdentityProviderConfigId.of(UUID.randomUUID()),
                "Sneaky",
                IdentityProviderType.OIDC,
                """
                        {
                          "issuer": "file:///etc/passwd",
                          "clientId": "numou",
                          "redirectUri": "https://app.example/cb",
                          "authorizationEndpoint": "file:///etc/passwd",
                          "tokenEndpoint": "https://idp.example/token",
                          "jwksUri": "https://idp.example/jwks"
                        }
                        """,
                null,
                Instant.parse("2026-10-20T10:00:00Z")
        );

        // requireHttpUrl is enforced before any network access - a file:// endpoint never gets read.
        var failure = assertThrows(ApplicationErrorException.class, () ->
                adapter.authorizationUrl(config, new FederationAuthorizationRequest("s", "n", "c"))
                        .await().indefinitely());
        assertEquals("FEDERATION_MISCONFIGURED", failure.error().code());
    }
}
