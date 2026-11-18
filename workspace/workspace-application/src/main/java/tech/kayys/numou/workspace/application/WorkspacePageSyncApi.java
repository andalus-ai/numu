package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.domain.PageRevision;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transport-neutral page synchronization API using trusted host identity. */
public final class WorkspacePageSyncApi {
    private final WorkspaceApi.IdentityProvider identity;
    private final WorkspacePageSyncService service;
    public WorkspacePageSyncApi(WorkspaceApi.IdentityProvider identity, WorkspacePageSyncService service) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.service = Objects.requireNonNull(service, "service");
    }
    public SyncBatchView changes(UUID pageId, long afterRevision, int limit) {
        Objects.requireNonNull(pageId, "pageId");
        WorkspaceApi.AuthenticatedActor actor = identity.currentActor();
        if (actor == null || actor.tenantId() == null || actor.actorId() == null)
            throw new WorkspaceApi.ApiException(401, "An authenticated tenant and actor are required");
        try {
            WorkspacePageSyncService.SyncBatch batch = service.changes(actor.tenantId(), actor.actorId(), pageId, afterRevision, limit);
            return new SyncBatchView(batch.currentRevision(), batch.requestedCursor(), batch.nextCursor(), batch.hasMore(),
                    batch.revisions().stream().map(RevisionView::from).toList());
        } catch (SecurityException denied) {
            throw new WorkspaceApi.ApiException(403, "Access denied", denied);
        } catch (IllegalArgumentException invalid) {
            throw new WorkspaceApi.ApiException(400, invalid.getMessage(), invalid);
        }
    }
    public record SyncBatchView(long currentRevision, long requestedCursor, long nextCursor, boolean hasMore, List<RevisionView> revisions) {
        public SyncBatchView { revisions = List.copyOf(revisions); }
    }
    public record RevisionView(long revision, String title, List<WorkspaceApi.BlockView> blocks, UUID actorId,
                               java.time.Instant recordedAt, String changeSummary) {
        public RevisionView { blocks = List.copyOf(blocks); }
        static RevisionView from(PageRevision revision) {
            return new RevisionView(revision.revision(), revision.title(), revision.blocks().stream().map(block ->
                    new WorkspaceApi.BlockView(block.id(), block.type().name(), block.text(), block.position(), block.attributes())).toList(),
                    revision.actorId(), revision.recordedAt(), revision.changeSummary());
        }
    }
}
