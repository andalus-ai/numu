package tech.kayys.numou.identity.adapter.inbound.rest.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.identity.application.dto.FederationCallback;
import tech.kayys.numou.identity.application.dto.FederatedAuthorizationStart;
import tech.kayys.numou.identity.application.service.FederatedAuthenticationService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Versioned OIDC callback for tenant-selection login. Configure the provider's registered
 * redirect URI to this resource; the legacy v1 callback remains unchanged.
 */
@Path("/api/v2/auth/federated")
@Tag(name = "Tenant federated authentication")
@Produces(MediaType.APPLICATION_JSON)
public class TenantFederatedAuthResource {
    static final String FLOW_COOKIE = "numou.federation.tenant";
    private static final int COOKIE_MAX_AGE_SECONDS = 600;
    private static final String COOKIE_PATH = "/api/v2/auth/federated";

    private final FederatedAuthenticationService federation;
    private final ObjectMapper objectMapper;
    private final boolean cookieSecure;

    @Inject
    public TenantFederatedAuthResource(
            FederatedAuthenticationService federation,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "numou.identity.federation.cookie-secure", defaultValue = "false")
            boolean cookieSecure) {
        this.federation = federation;
        this.objectMapper = objectMapper;
        this.cookieSecure = cookieSecure;
    }

    private record FlowCookie(String state, String nonce, String verifier) { }

    @GET
    @Path("/{providerId}/authorize")
    @Operation(summary = "Start OIDC login that ends with a tenant-selection credential")
    public Uni<Response> authorize(@PathParam("providerId") java.util.UUID providerId) {
        return federation.begin(providerId)
                .map(start -> Response.seeOther(URI.create(start.authorizationUrl()))
                        .cookie(flowCookie(encodeFlow(start))).build());
    }

    @GET
    @Path("/{providerId}/callback")
    @Operation(summary = "Complete OIDC login and issue a tenant-selection credential")
    public Uni<Response> callback(
            @PathParam("providerId") java.util.UUID providerId,
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String providerError,
            @CookieParam(FLOW_COOKIE) String cookie) {
        if (providerError != null && !providerError.isBlank()) {
            return failure("FEDERATION_DENIED",
                    providerError.matches("^[A-Za-z0-9_.-]{1,64}$") ? providerError : "provider_error");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return failure("FEDERATION_STATE_INVALID",
                    "Missing authorization code or state; restart the login");
        }

        final FlowCookie flow;
        try {
            flow = decodeFlow(cookie);
        } catch (Exception invalidCookie) {
            return failure("FEDERATION_STATE_INVALID",
                    "Missing or unreadable flow cookie; restart the login");
        }
        if (!flow.state().equals(state)) {
            return failure("FEDERATION_STATE_INVALID", "State mismatch; restart the login");
        }

        return federation.completeForTenantSelection(providerId,
                        new FederationCallback(code, state, flow.verifier(), flow.nonce()))
                .map(result -> Response.ok(result).cookie(clearedFlowCookie()).build());
    }

    private String encodeFlow(FederatedAuthorizationStart start) {
        try {
            String json = objectMapper.writeValueAsString(
                    new FlowCookie(start.state(), start.nonce(), start.codeVerifier()));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encode federation flow cookie", ex);
        }
    }

    private FlowCookie decodeFlow(String value) throws Exception {
        if (value == null || value.isBlank()) {
            throw new ApplicationErrorException(
                    ApplicationError.of("FEDERATION_STATE_INVALID", "Flow cookie missing"));
        }
        byte[] json = Base64.getUrlDecoder().decode(value);
        FlowCookie flow = objectMapper.readValue(json, FlowCookie.class);
        if (flow.state() == null || flow.nonce() == null || flow.verifier() == null) {
            throw new ApplicationErrorException(
                    ApplicationError.of("FEDERATION_STATE_INVALID", "Flow cookie incomplete"));
        }
        return flow;
    }

    private NewCookie flowCookie(String value) {
        return new NewCookie(FLOW_COOKIE, value, COOKIE_PATH, null,
                NewCookie.DEFAULT_VERSION, null, COOKIE_MAX_AGE_SECONDS,
                null, cookieSecure, true, NewCookie.SameSite.LAX);
    }

    private NewCookie clearedFlowCookie() {
        return new NewCookie(FLOW_COOKIE, "", COOKIE_PATH, null,
                NewCookie.DEFAULT_VERSION, null, 0,
                null, cookieSecure, true, NewCookie.SameSite.LAX);
    }

    private static <T> Uni<T> failure(String code, String message) {
        return Uni.createFrom().failure(ApplicationError.of(code, message).toException());
    }
}
