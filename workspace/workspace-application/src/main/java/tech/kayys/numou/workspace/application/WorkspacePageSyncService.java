package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.domain.Page;
import tech.kayys.numou.workspace.domain.PageRevision;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded, resumable page-revision feed for reconnecting and polling clients. */
public final class WorkspacePageSyncService {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    private final PageRepository pages;
    private final AccessPolicy access;

    public WorkspacePageSyncService(PageRepository pages, AccessPolicy access) {
        this.pages = Objects.requireNonNull(pages, "pages");
        this.access = Objects.requireNonNull(access, "access");
    }

    public SyncBatch changes(UUID tenantId, UUID actorId, UUID pageId, long afterRevision, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(pageId, "pageId");
        if (afterRevision < 0) throw new IllegalArgumentException("afterRevision must not be negative");
        if (limit < 1 || limit > MAX_LIMIT) throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        Page current = pages.findPage(tenantId, pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found for tenant"));
        access.require(tenantId, actorId, Resource.PAGE, pageId, Action.READ);
        if (afterRevision > current.revision()) {
            throw new IllegalArgumentException("afterRevision cannot exceed the current page revision");
        }
        // Bound the query to the revision observed above. A concurrent later write is picked up next poll.
        List<PageRevision> fetched = pages.historyAfter(tenantId, pageId, afterRevision, current.revision(), limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<PageRevision> visible = List.copyOf(fetched.subList(0, Math.min(limit, fetched.size())));
        long nextCursor = visible.isEmpty() ? afterRevision : visible.get(visible.size() - 1).revision();
        return new SyncBatch(current.revision(), afterRevision, nextCursor, hasMore, visible);
    }

    public record SyncBatch(long currentRevision, long requestedCursor, long nextCursor,
                            boolean hasMore, List<PageRevision> revisions) {
        public SyncBatch {
            if (currentRevision < 1 || requestedCursor < 0 || nextCursor < requestedCursor)
                throw new IllegalArgumentException("Invalid page sync cursor state");
            revisions = List.copyOf(revisions);
        }
    }
}
