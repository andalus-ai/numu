package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.identity.application.service.OAuthClientCredentialsGrantService;
import tech.kayys.numou.identity.application.service.OAuthGrantException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Path("/api/v1/oauth2")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthTokenResource {
    private final OAuthClientCredentialsGrantService grantService;
    @Inject public OAuthTokenResource(OAuthClientCredentialsGrantService grantService) { this.grantService = grantService; }

    @POST @Path("/token") @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Uni<Response> token(@jakarta.ws.rs.HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                               @FormParam("grant_type") String grantType, @FormParam("scope") String scope) {
        if (!"client_credentials".equals(grantType))
            return Uni.createFrom().item(error("unsupported_grant_type", 400, false));
        final String[] client;
        try { client = parseBasicCredentials(authorization); }
        catch (RuntimeException invalid) { return Uni.createFrom().item(error("invalid_client", 401, true)); }
        return grantService.exchange(client[0], client[1], scope)
                .map(result -> Response.ok(result).header("Cache-Control", "no-store").header("Pragma", "no-cache").build())
                .onFailure(OAuthGrantException.class).recoverWithItem(failure -> {
                    OAuthGrantException oauth = (OAuthGrantException) failure;
                    return error(oauth.error(), oauth.httpStatus(), oauth.httpStatus() == 401);
                })
                .onFailure().recoverWithItem(failure -> error("server_error", 500, false));
    }

    private static String[] parseBasicCredentials(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6))
            throw new IllegalArgumentException("Basic auth required");
        String encoded = header.substring(6).trim();
        if (encoded.isEmpty() || encoded.length() > 4096) throw new IllegalArgumentException("invalid auth");
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String pair = new String(decoded, StandardCharsets.UTF_8);
        int separator = pair.indexOf(':');
        if (separator <= 0 || separator == pair.length() - 1) throw new IllegalArgumentException("invalid auth");
        return new String[]{pair.substring(0, separator), pair.substring(separator + 1)};
    }

    private static Response error(String code, int status, boolean challenge) {
        Response.ResponseBuilder builder = Response.status(status).entity(new OAuthErrorResponse(code))
                .type(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").header("Pragma", "no-cache");
        if (challenge) builder.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Numou OAuth2 Token Endpoint\"");
        return builder.build();
    }
    public record OAuthErrorResponse(String error) {}
}
