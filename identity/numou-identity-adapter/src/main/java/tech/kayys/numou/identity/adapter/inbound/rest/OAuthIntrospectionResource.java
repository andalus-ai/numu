package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.identity.application.service.OAuthClientAccessTokenStateValidator;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RFC 7662-style internal introspection endpoint. Callers must authenticate as
 * the dedicated oauth-introspect service role over the deployment's mTLS channel.
 */
@Path("/internal/v1/oauth2")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthIntrospectionResource {
    private static final int MAX_TOKEN_LENGTH = 16_384;
    private final JWTParser jwtParser;
    private final OAuthClientAccessTokenStateValidator stateValidator;
    private final String expectedAudience;

    @Inject
    public OAuthIntrospectionResource(JWTParser jwtParser,
            OAuthClientAccessTokenStateValidator stateValidator,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "numou.oauth.access-token.audience") String expectedAudience) {
        this.jwtParser = jwtParser;
        this.stateValidator = stateValidator;
        this.expectedAudience = expectedAudience;
    }

    @POST
    @Path("/introspect")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @RolesAllowed("oauth-introspect")
    public Uni<Response> introspect(@FormParam("token") String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return inactive();
        }
        final JWTCallerPrincipal principal;
        try {
            // JWTParser verifies signature and configured issuer using Identity's trusted public key.
            principal = jwtParser.parse(token);
        } catch (Exception invalid) {
            return inactive();
        }
        if (principal.getExpirationTime() <= System.currentTimeMillis() / 1000
                || !principal.getAudience().contains(expectedAudience)) {
            return inactive();
        }
        String scopeClaim = principal.getClaim("scope");
        Set<String> scopes = scopeClaim == null ? Set.of()
                : Arrays.stream(scopeClaim.trim().split("\\s+")).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
        Number version = principal.getClaim("credential_version");
        if (version == null || version.doubleValue() != version.longValue()) return inactive();
        return stateValidator.validate(principal.getClaim("token_use"), principal.getClaim("client_id"),
                principal.getClaim("tenant_id"), principal.getClaim("application_id"),
                version.longValue(), scopes)
                .map(verified -> Response.ok(new IntrospectionResponse(true, "Bearer",
                        verified.clientId(), verified.tenantId().toString(), verified.applicationId().toString(),
                        verified.scopes().stream().sorted().collect(Collectors.joining(" "))))
                        .header("Cache-Control", "no-store").build())
                .onFailure().recoverWithItem(failure -> Response.ok(IntrospectionResponse.inactive())
                        .header("Cache-Control", "no-store").build());
    }

    private Uni<Response> inactive() {
        return Uni.createFrom().item(Response.ok(IntrospectionResponse.inactive())
                .header("Cache-Control", "no-store").build());
    }

    public record IntrospectionResponse(boolean active, String token_type, String client_id,
            String tenant_id, String application_id, String scope) {
        static IntrospectionResponse inactive() {
            return new IntrospectionResponse(false, null, null, null, null, null);
        }
    }
}
