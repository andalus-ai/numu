package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.identity.application.dto.AccessTokenResponse;
import tech.kayys.numou.identity.application.dto.LoginRequest;
import tech.kayys.numou.identity.application.service.TokenService;

/**
 * Local credential login endpoint exchanging username/password for a short-lived access JWT.
 */
@Path("/api/v1/auth")
@Tag(name = "Auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private final TokenService tokenService;

    @Inject
    public AuthResource(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @POST
    @Path("/login")
    @Operation(summary = "Exchange local username/password for a short-lived access JWT")
    public Uni<AccessTokenResponse> login(@Valid LoginRequest request) {
        return tokenService.loginLocal(request);
    }
}
