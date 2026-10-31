package tech.kayys.numou.workspace.adapter.rest;
import jakarta.inject.Inject; import jakarta.ws.rs.DELETE; import jakarta.ws.rs.GET; import jakarta.ws.rs.POST; import jakarta.ws.rs.Path; import jakarta.ws.rs.PathParam; import jakarta.ws.rs.Consumes; import jakarta.ws.rs.Produces; import jakarta.ws.rs.core.MediaType; import tech.kayys.numou.workspace.application.WorkspacePresenceApi; import java.util.UUID;
/** HTTP transport for advisory page presence leases. */
@Path("/api/v1/pages/{pageId}/presence") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class WorkspacePresenceResource {
 private final WorkspacePresenceApi presence;
 @Inject public WorkspacePresenceResource(WorkspacePresenceApi presence){this.presence=presence;}
 @GET public tech.kayys.numou.workspace.application.WorkspacePresenceService.PresenceSnapshot list(@PathParam("pageId") UUID page){return presence.list(page);}
 @POST @Path("/{sessionId}/heartbeat") public tech.kayys.numou.workspace.application.WorkspacePresenceService.PresenceSnapshot heartbeat(@PathParam("pageId") UUID page,@PathParam("sessionId") UUID session){return presence.heartbeat(page,session);}
 @DELETE @Path("/{sessionId}") public void leave(@PathParam("pageId") UUID page,@PathParam("sessionId") UUID session){presence.leave(page,session);}
}
