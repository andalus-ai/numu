package tech.kayys.numou.workspace.adapter.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tech.kayys.numou.workspace.application.WorkspacePageSyncApi;
import java.util.UUID;

/** Bounded pull/replay endpoint; clients resume from the last fully applied revision. */
@Path("/api/v1/pages/{pageId}/changes")
@Produces(MediaType.APPLICATION_JSON)
public class WorkspacePageSyncResource {
    private final WorkspacePageSyncApi sync;
    @Inject public WorkspacePageSyncResource(WorkspacePageSyncApi sync) { this.sync = sync; }

    @GET
    public WorkspacePageSyncApi.SyncBatchView changes(
            @PathParam("pageId") UUID pageId,
            @QueryParam("afterRevision") @DefaultValue("0") long afterRevision,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return sync.changes(pageId, afterRevision, limit);
    }
}
