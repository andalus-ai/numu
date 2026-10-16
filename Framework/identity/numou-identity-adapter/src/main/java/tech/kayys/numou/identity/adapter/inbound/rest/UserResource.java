package tech.kayys.syirkah.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.syirkah.foundation.application.page.Page;
import tech.kayys.syirkah.foundation.application.page.PageRequest;
import tech.kayys.syirkah.identity.adapter.inbound.rest.dto.ChangeEmailRequest;
import tech.kayys.syirkah.identity.adapter.inbound.rest.dto.DeactivateUserRequest;
import tech.kayys.syirkah.identity.adapter.inbound.rest.dto.RegisterUserRequest;
import tech.kayys.syirkah.identity.adapter.inbound.rest.dto.UserResponse;
import tech.kayys.syirkah.identity.application.command.ChangeUserEmailCommand;
import tech.kayys.syirkah.identity.application.command.ChangeUserEmailCommandHandler;
import tech.kayys.syirkah.identity.application.command.DeactivateUserCommand;
import tech.kayys.syirkah.identity.application.command.DeactivateUserCommandHandler;
import tech.kayys.syirkah.identity.application.command.RegisterUserCommand;
import tech.kayys.syirkah.identity.application.command.RegisterUserCommandHandler;
import tech.kayys.syirkah.identity.application.port.CurrentPrincipalPort;
import tech.kayys.syirkah.identity.application.query.GetUserByIdQuery;
import tech.kayys.syirkah.identity.application.query.GetUserByIdQueryHandler;
import tech.kayys.syirkah.identity.application.query.ListUsersQuery;
import tech.kayys.syirkah.identity.application.query.ListUsersQueryHandler;
import tech.kayys.syirkah.identity.application.query.UserView;

import java.net.URI;
import java.util.UUID;
import tech.kayys.syirkah.identity.application.security.PrincipalType;

/**
 * Thin inbound HTTP adapter. It does exactly two things: translate
 * HTTP <-> Command/Query, and translate ApplicationErrorException <->
 * an HTTP status. No business logic lives here - see
 * RegisterUserCommandHandler etc. for that.
 */
@Path("/api/identity/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    RegisterUserCommandHandler registerUserCommandHandler;

    @Inject
    ChangeUserEmailCommandHandler changeUserEmailCommandHandler;

    @Inject
    DeactivateUserCommandHandler deactivateUserCommandHandler;

    @Inject
    GetUserByIdQueryHandler getUserByIdQueryHandler;

    @Inject
    ListUsersQueryHandler listUsersQueryHandler;

    @Inject
    CurrentPrincipalPort currentPrincipal;

    @POST
    public Uni<Response> register(RegisterUserRequest request) {
        var command = new RegisterUserCommand(
                request.email(),
                request.password(),
                request.displayName()
        );

        return registerUserCommandHandler.handle(command)
                .map(userId -> Response
                        .created(URI.create("/api/identity/users/" + userId.value()))
                        .build()
                );
    }

    @GET
    @Path("/{userId}")
    public Uni<UserResponse> getById(@PathParam("userId") UUID userId) {
        return requireSelf(userId)
                .chain(() -> getUserByIdQueryHandler.handle(new GetUserByIdQuery(userId)))
                .map(UserResponse::from);
    }

    @GET
    public Uni<Page<UserResponse>> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return Uni.createFrom().failure(
                new jakarta.ws.rs.ForbiddenException("User enumeration requires a tenant-scoped query")
        );
    }

    @PATCH
    @Path("/{userId}/email")
    public Uni<Response> changeEmail(
            @PathParam("userId") UUID userId,
            ChangeEmailRequest request
    ) {
        var command = new ChangeUserEmailCommand(userId, request.newEmail());

        return requireSelf(userId)
                .chain(() -> changeUserEmailCommandHandler.handle(command))
                .replaceWith(Response.noContent().build());
    }

    @POST
    @Path("/{userId}/deactivate")
    public Uni<Response> deactivate(
            @PathParam("userId") UUID userId,
            DeactivateUserRequest request
    ) {
        var command = new DeactivateUserCommand(userId, request.reason());

        return requireSelf(userId)
                .chain(() -> deactivateUserCommandHandler.handle(command))
                .replaceWith(Response.noContent().build());
    }

    private Uni<Void> requireSelf(UUID requestedUserId) {
        return currentPrincipal.current().flatMap(principal -> {
            if (principal.type() != PrincipalType.USER
                    || principal.userId() == null
                    || !principal.userId().value().equals(requestedUserId)) {
                return Uni.createFrom().failure(
                        new jakarta.ws.rs.ForbiddenException("A user may only access their own profile")
                );
            }
            return Uni.createFrom().voidItem();
        });
    }

}
