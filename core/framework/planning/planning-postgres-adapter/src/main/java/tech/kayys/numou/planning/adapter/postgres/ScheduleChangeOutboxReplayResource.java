package tech.kayys.numou.planning.adapter.postgres;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ConflictException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Restricted, auditable recovery operations for the schedule-change outbox.
 * Host authentication and the platform-operations role mapping are required.
 */
@Path("/internal/operations/planning/outbox")
@ApplicationScoped
@RolesAllowed("platform-operations")
@Produces(MediaType.APPLICATION_JSON)
public class ScheduleChangeOutboxReplayResource {
    private final JdbcScheduleChangeOutboxRelayStore store;

    @Inject
    public ScheduleChangeOutboxReplayResource(JdbcScheduleChangeOutboxRelayStore store) {
        this.store = store;
    }

    @POST
    @Path("/{outboxId}/replay")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response replay(
            @PathParam("outboxId") UUID outboxId,
            ReplayRequest request,
            SecurityContext securityContext) {
        if (request == null || request.reason() == null
                || request.reason().isBlank() || request.reason().trim().length() > 500) {
            throw new BadRequestException("A replay reason of 1 to 500 characters is required");
        }
        if (securityContext == null || securityContext.getUserPrincipal() == null
                || securityContext.getUserPrincipal().getName() == null
                || securityContext.getUserPrincipal().getName().isBlank()) {
            throw new NotAuthorizedException("Authenticated operator identity is required");
        }

        boolean replayed = store.replayDeadLetter(
                outboxId,
                securityContext.getUserPrincipal().getName(),
                request.reason(),
                Instant.now());
        if (!replayed) {
            throw new ConflictException("Event is not currently eligible for dead-letter replay");
        }
        return Response.accepted(new ReplayAccepted(outboxId, "queued")).build();
    }

    public record ReplayRequest(String reason) {
    }

    public record ReplayAccepted(UUID outboxId, String status) {
    }
}
