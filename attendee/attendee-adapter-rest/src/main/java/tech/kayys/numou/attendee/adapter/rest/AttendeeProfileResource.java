package tech.kayys.numou.attendee.adapter.rest;

import tech.kayys.numou.attendee.application.command.CreateAttendeeProfileCommand;
import tech.kayys.numou.attendee.application.command.CreateAttendeeProfileHandler;
import tech.kayys.numou.attendee.application.command.CreateAttendeeProfileResult;
import tech.kayys.numou.attendee.application.query.GetAttendeeProfileHandler;
import tech.kayys.numou.attendee.application.query.GetAttendeeProfileQuery;
import tech.kayys.numou.attendee.domain.AttendeeProfile;
import tech.kayys.numou.foundation.application.result.Result;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

/** Versioned inbound API. Tenant and actor identifiers are never accepted in request bodies. */
@Path("/api/v1/attendee-profiles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AttendeeProfileResource {
    private final CreateAttendeeProfileHandler create;
    private final GetAttendeeProfileHandler get;

    @Inject
    public AttendeeProfileResource(CreateAttendeeProfileHandler create, GetAttendeeProfileHandler get) {
        this.create = create;
        this.get = get;
    }

    @POST
    public io.smallrye.mutiny.Uni<Response> create(CreateProfileRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        final CreateAttendeeProfileCommand command;
        try {
            command = new CreateAttendeeProfileCommand(request.partyId(), request.profileKind(),
                    request.displayName(), request.preferredName(), request.preferredLanguage());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException(e.getMessage());
        }
        return create.handle(command).map(result -> result.fold(
                value -> Response.status(Response.Status.CREATED).entity(ProfileCreated.from(value)).build(),
                error -> errorResponse(error.code(), error.message())));
    }

    @GET
    @Path("/{profileId}")
    public io.smallrye.mutiny.Uni<Response> get(@PathParam("profileId") String profileId) {
        final UUID id;
        try { id = UUID.fromString(profileId); }
        catch (IllegalArgumentException e) { throw new BadRequestException("profileId must be a UUID"); }
        return get.handle(new GetAttendeeProfileQuery(id)).map(found -> found
                .<Response>map(profile -> Response.ok(ProfileView.from(profile)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("code", "ATTENDEE_PROFILE_NOT_FOUND", "message", "Attendee profile not found")).build()));
    }

    private static Response errorResponse(String code, String message) {
        int status = switch (code) {
            case "ATTENDEE_PROFILE_ALREADY_EXISTS" -> 409;
            case "FORBIDDEN", "ACCESS_DENIED", "UNAUTHENTICATED" -> 403;
            case "NOT_FOUND", "ATTENDEE_PROFILE_NOT_FOUND" -> 404;
            default -> 400;
        };
        return Response.status(status).entity(Map.of("code", code, "message", message)).build();
    }

    public record CreateProfileRequest(UUID partyId, String profileKind, String displayName,
                                       String preferredName, String preferredLanguage) {}
    public record ProfileCreated(UUID profileId, String status, long revision) {
        static ProfileCreated from(CreateAttendeeProfileResult result) {
            return new ProfileCreated(result.profileId(), result.status().name(), result.revision());
        }
    }
    public record ProfileView(UUID profileId, UUID partyId, String profileKind, String displayName,
                              String preferredName, String preferredLanguage, String status,
                              long revision) {
        static ProfileView from(AttendeeProfile profile) {
            return new ProfileView(profile.id(), profile.partyId().value(), profile.profileKind(),
                    profile.displayName(), profile.preferredName(), profile.preferredLanguage(),
                    profile.status().name(), profile.revision());
        }
    }
}