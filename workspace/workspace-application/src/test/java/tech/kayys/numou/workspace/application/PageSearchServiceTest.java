package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PageSearchServiceTest {
    @Test void searchScopesWorkspaceAndFiltersPagesActorCannotRead() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, tenant, "Docs", "", Instant.EPOCH);
        UUID allowedId = UUID.randomUUID(), deniedId = UUID.randomUUID(), spaceId = UUID.randomUUID();
        PageSearchHit allowed = new PageSearchHit(allowedId, workspaceId, spaceId, null, "Runbook", Instant.EPOCH);
        PageSearchHit denied = new PageSearchHit(deniedId, workspaceId, spaceId, null, "Secrets", Instant.EPOCH);
        List<Integer> limits = new ArrayList<>();
        WorkspaceRepository workspaces = new WorkspaceRepository() {
            public boolean save(Workspace value) { return false; }
            public Optional<Workspace> findWorkspace(UUID t, UUID id) {
                return t.equals(tenant) && id.equals(workspaceId) ? Optional.of(workspace) : Optional.empty();
            }
        };
        SpaceRepository spaces = new SpaceRepository() {
            public boolean save(Space value) { return false; }
            public Optional<Space> find(UUID t, UUID w, UUID s) {
                return Optional.of(new Space(s, t, w, "General", "general", Instant.EPOCH));
            }
            public boolean slugExists(UUID t, UUID w, String slug) { return false; }
        };
        PageSearchService service = new PageSearchService(
                workspaces, spaces,
                (t, w, s, q, limit) -> {
                    assertEquals(workspaceId, w);
                    assertEquals("runbook", q);
                    limits.add(limit);
                    return List.of(allowed, denied);
                },
                (t, u, resource, id, action) -> {
                    if (resource == Resource.PAGE && id.equals(deniedId)) throw new SecurityException("denied");
                });

        List<PageSearchHit> result = service.search(tenant, actor, workspaceId, null, " runbook ", 500);
        assertEquals(List.of(allowed), result);
        assertEquals(List.of(100), limits);
    }

    @Test void validatesQueryAndLimitBeforeRepositoryAccess() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), workspaceId = UUID.randomUUID();
        WorkspaceRepository workspaces = new WorkspaceRepository() {
            public boolean save(Workspace value) { return false; }
            public Optional<Workspace> findWorkspace(UUID t, UUID w) { return Optional.empty(); }
        };
        SpaceRepository spaces = new SpaceRepository() {
            public boolean save(Space value) { return false; }
            public Optional<Space> find(UUID t, UUID w, UUID s) { return Optional.empty(); }
            public boolean slugExists(UUID t, UUID w, String slug) { return false; }
        };
        PageSearchService service = new PageSearchService(workspaces, spaces,
                (t, w, s, q, limit) -> List.of(), (t, u, r, id, a) -> {});
        assertThrows(IllegalArgumentException.class, () -> service.search(tenant, actor, workspaceId, null, "  ", 10));
        assertThrows(IllegalArgumentException.class, () -> service.search(tenant, actor, workspaceId, null, "query", 0));
    }
}
