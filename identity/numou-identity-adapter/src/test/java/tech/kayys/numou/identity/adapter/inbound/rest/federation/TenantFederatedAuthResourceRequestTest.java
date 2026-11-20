package tech.kayys.numou.identity.adapter.inbound.rest.federation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.adapter.outbound.postgres.IdentityPostgresTestResource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(IdentityPostgresTestResource.class)
class TenantFederatedAuthResourceRequestTest {
    private static final String COOKIE_NAME = TenantFederatedAuthResource.FLOW_COOKIE;

    @TestHTTPResource("/")
    URI baseUri;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void callbackRejectsMissingAuthorizationParametersBeforeCallingProvider() throws Exception {
        HttpResponse<String> response = get(callbackPath() + "?code=auth-code");

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("FEDERATION_STATE_INVALID"), response.body());
    }

    @Test
    void callbackRejectsMissingOrMalformedFlowCookie() throws Exception {
        String path = callbackPath() + "?code=auth-code&state=expected-state";

        HttpResponse<String> missing = get(path);
        assertEquals(400, missing.statusCode(), missing.body());
        assertTrue(missing.body().contains("FEDERATION_STATE_INVALID"), missing.body());

        HttpResponse<String> malformed = get(path, COOKIE_NAME + "=not-base64");
        assertEquals(400, malformed.statusCode(), malformed.body());
        assertTrue(malformed.body().contains("FEDERATION_STATE_INVALID"), malformed.body());
    }

    @Test
    void callbackRejectsStateMismatchBeforeCallingProvider() throws Exception {
        String cookie = encodeFlowCookie("cookie-state", "nonce", "verifier");
        HttpResponse<String> response = get(
                callbackPath() + "?code=auth-code&state=query-state",
                COOKIE_NAME + "=" + cookie);

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("FEDERATION_STATE_INVALID"), response.body());
        assertTrue(response.body().contains("State mismatch"), response.body());
    }

    @Test
    void callbackReturnsSanitizedProviderErrorWithoutExposingProviderText() throws Exception {
        HttpResponse<String> response = get(callbackPath() + "?error=access_denied%0Asecret");

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("FEDERATION_DENIED"), response.body());
        assertTrue(response.body().contains("provider_error"), response.body());
        assertFalse(response.body().contains("secret"), response.body());
    }

    private String callbackPath() {
        return "api/v2/auth/federated/" + UUID.randomUUID() + "/callback";
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Accept", "application/json").GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encodeFlowCookie(String state, String nonce, String verifier) {
        String json = "{\"state\":\"" + state + "\",\"nonce\":\"" + nonce
                + "\",\"verifier\":\"" + verifier + "\"}";
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
