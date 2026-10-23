package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.PageSearchHit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped search use case; page-level permissions are applied before results are returned. */
public final class PageSearchService {
    private static final int MAX_LIMIT = 100;
    private final WorkspaceRepository workspaces;
    private final SpaceRepository spaces;
    private final PageSearchRepository search;
    private final AccessPolicy access;

    public PageSearchService(WorkspaceRepository workspaces, SpaceRepository spaces,
                             PageSearchRepository search, AccessPolicy access) {
        this.workspaces = Objects.requireNonNull(workspaces);
        this.spaces = Objects.requireNonNull(spaces);
        this.search = Objects.requireNonNull(search);
        this.access = Objects.requireNonNull(access);
    }

    public List<PageSearchHit> search(UUID tenantId, UUID actorId, UUID workspaceId,
                                      UUID spaceId, String query, int requestedLimit) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Search query is required");
        String normalized = query.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("Search query must be at most 200 characters");
        if (requestedLimit < 1) throw new IllegalArgumentException("Search limit must be positive");

        workspaces.findWorkspace(tenantId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found for tenant"));
        access.require(tenantId, actorId, Resource.WORKSPACE, workspaceId, Action.READ);
        if (spaceId != null) {
            spaces.find(tenantId, workspaceId, spaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Space not found for workspace"));
            access.require(tenantId, actorId, Resource.SPACE, spaceId, Action.READ);
        }

        return search.searchPages(tenantId, workspaceId, spaceId, normalized, Math.min(requestedLimit, MAX_LIMIT))
                .stream()
                .filter(hit -> {
                    try {
                        access.require(tenantId, actorId, Resource.PAGE, hit.pageId(), Action.READ);
                        return true;
                    } catch (SecurityException denied) {
                        return false;
                    }
                })
                .toList();
    }
}
