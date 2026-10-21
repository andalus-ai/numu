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
import tech.kayys.numou.identity.application.dto.FederatedLoginResponse;
import tech.kayys.numou.identity.application.port.IdentityProviderConfigRepository;
import tech.kayys.numou.identity.application.service.FederatedAuthenticationService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * OIDC/OAuth2 browser flow endpoints (blueprint: "OIDC/OAuth2 discovery and callback endpoint"):
 *
 * <ul>
 *   <li>{@code GET /api/v1/auth/federated/providers} - enabled providers for a login picker;</li>
 *   <li>{@code GET /api/v1/auth/federated/{id}/authorize} - 302 to the provider with PKCE
 *       state/nonce/verifier kept in an HttpOnly, SameSite=Lax cookie;</li>
 *   <li>{@code GET /api/v1/auth/federated/{id}/callback} - validates the state against the cookie,
 *       completes the JIT-provisioned login and returns an internal Numou access token.</li>
 * </ul>
 *
 * <p>Public endpoints: they sit ahead of authentication by design (they <i>are</i> the login).
 * The cookie is the only place the PKCE verifier lives between the two hops, so it is HttpOnly,
 * short-lived and single-use with the provider's authorization code.
 */
@Path("/api/v1/auth/federated")
@Tag(name = "Federation")
@Produces(MediaType.APPLICATION_JSON)
public class FederatedAuthResource {

    public static final String FLOW_COOKIE = "numou.federation";
    private static final int FLOW_COOKIE_MAX_AGE_SECONDS = 600;
    private static final String COOKIE_PATH = "/api/v1/auth/federated";

    private final FederatedAuthenticationService federation;
    private final IdentityProviderConfigRepository providerConfigs;
    private final ObjectMapper objectMapper;
    private final boolean cookieSecure;

    @Inject
    public FederatedAuthResource(
            FederatedAuthenticationService federation,
            IdentityProviderConfigRepository providerConfigs,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "numou.identity.federation.cookie-secure", defaultValue = "false")
            boolean cookieSecure
    ) {
        this.federation = federation;
        this.providerConfigs = providerConfigs;
        this.objectMapper = objectMapper;
        this.cookieSecure = cookieSecure;
    }

    private record FlowCookie(String state, String nonce, String verifier) { }

    record ProviderSummary(UUID id, String name, String type) { }

    @GET
    @Path("/providers")
    @Operation(summary = "List enabled identity providers for federated login")
    public Uni<List<ProviderSummary>> providers() {
        return providerConfigs.findEnabled()
                .map(list -> list.stream()
                        .map(config -> new ProviderSummary(
                                config.id().value(), config.name(), config.type().name()))
                        .toList());
    }

    @GET
    @Path("/{providerId}/authorize")
    @Operation(summary = "Start a federated login: redirects to the identity provider (PKCE)")
    public Uni<Response> authorize(@PathParam("providerId") UUID providerId) {
        return federation.begin(providerId)
                .map(start -> Response.seeOther(URI.create(start.authorizationUrl()))
                        .cookie(flowCookie(encodeFlow(start)))
                        .build());
    }

    @GET
    @Path("/{providerId}/callback")
    @Operation(summary = "Provider callback: exchanges the code and returns an internal access token")
    public Uni<Response> callback(
            @PathParam("providerId") UUID providerId,
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String providerError,
            @CookieParam(FLOW_COOKIE) String flowCookie
    ) {
        if (providerError != null && !providerError.isBlank()) {
            return failure("FEDERATION_DENIED",
                    providerError.matches("^[A-Za-z0-9_.-]{1,64}$") ? providerError : "provider_error");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return failure("FEDERATION_STATE_INVALID", "Missing authorization code or state; restart the login");
        }

        final FlowCookie flow;
        try {
            flow = decodeFlow(flowCookie);
        } catch (Exception ex) {
            return failure("FEDERATION_STATE_INVALID", "Missing or unreadable flow cookie; restart the login");
        }
        if (!flow.state().equals(state)) {
            return failure("FEDERATION_STATE_INVALID", "State mismatch; restart the login");
        }

        return federation.complete(providerId, new FederationCallback(code, state, flow.verifier(), flow.nonce()))
                .map(result -> Response.ok(result).cookie(clearedFlowCookie()).build());
    }

    // ------------------------------------------------------------------ flow cookie

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

    private FlowCookie decodeFlow(String cookieValue) throws Exception {
        if (cookieValue == null || cookieValue.isBlank()) {
            throw new ApplicationErrorException(
                    ApplicationError.of("FEDERATION_STATE_INVALID", "Flow cookie missing"));
        }
        byte[] json = Base64.getUrlDecoder().decode(cookieValue);
        FlowCookie flow = objectMapper.readValue(json, FlowCookie.class);
        if (flow.state() == null || flow.nonce() == null || flow.verifier() == null) {
            throw new ApplicationErrorException(
                    ApplicationError.of("FEDERATION_STATE_INVALID", "Flow cookie incomplete"));
        }
        return flow;
    }

    private NewCookie flowCookie(String value) {
        // httpOnly: the PKCE verifier must never be readable from JavaScript.
        return new NewCookie(
                FLOW_COOKIE, value, COOKIE_PATH, null,
                NewCookie.DEFAULT_VERSION, null, FLOW_COOKIE_MAX_AGE_SECONDS,
                null, cookieSecure, true, NewCookie.SameSite.LAX);
    }

    private NewCookie clearedFlowCookie() {
        return new NewCookie(
                FLOW_COOKIE, "", COOKIE_PATH, null,
                NewCookie.DEFAULT_VERSION, null, 0,
                null, cookieSecure, true, NewCookie.SameSite.LAX);
    }

    private static <T> Uni<T> failure(String code, String message) {
        return Uni.createFrom().failure(ApplicationError.of(code, message).toException());
    }
}
