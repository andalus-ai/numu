package tech.kayys.numou.identity.adapter.inbound.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.adapter.outbound.postgres.IdentityPostgresTestResource;
import tech.kayys.numou.identity.adapter.outbound.security.TenantJwtTokenIssuerAdapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(IdentityPostgresTestResource.class)
class TenantSelectionCredentialRequestTest {
    private static final String FILTER_DENIAL =
            "Tenant-selection credential cannot access this resource";

    @TestHTTPResource("/")
    URI baseUri;

    @Inject
    TenantJwtTokenIssuerAdapter tokenIssuer;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void selectionCredentialCanDiscoverTenantsButCannotCallManagementApi() throws Exception {
        String token = tokenIssuer.issueTenantSelectionToken(UUID.randomUUID(), "Selection Test");

        HttpResponse<String> discovery = request("GET", "api/v2/auth/tenants", token);
        assertEquals(200, discovery.statusCode(), discovery.body());

        HttpResponse<String> management = request("GET", "api/v1/service-accounts", token);
        assertEquals(403, management.statusCode(), management.body());
        assertTrue(management.body().contains(FILTER_DENIAL), management.body());
    }

    @Test
    void tenantBoundAccessCredentialIsNotRejectedBySelectionBoundary() throws Exception {
        String token = tokenIssuer.issueTenantAccessToken(
                UUID.randomUUID(), "Access Test", UUID.randomUUID());

        HttpResponse<String> response = request("GET", "api/v1/service-accounts", token);

        assertFalse(response.body().contains(FILTER_DENIAL), response.body());
    }

    private HttpResponse<String> request(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
        } else {
            builder.GET();
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
