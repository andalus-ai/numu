package tech.kayys.numou.workspace.collaboration.quarkus;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.numou.workspace.collaboration.local.LocalWorkspacePageChangeHub;

/** Maps bounded-hub capacity rejection to an explicit retryable HTTP response. */
@Provider
public class WorkspaceCollaborationCapacityExceptionMapper
        implements ExceptionMapper<LocalWorkspacePageChangeHub.SubscriptionLimitExceededException> {

    @Override
    public Response toResponse(LocalWorkspacePageChangeHub.SubscriptionLimitExceededException exception) {
        return Response.status(429).build();
    }
}
