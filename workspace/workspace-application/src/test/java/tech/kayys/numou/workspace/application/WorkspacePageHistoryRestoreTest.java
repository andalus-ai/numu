package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspaceApi.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePageHistoryRestoreTest {
    @Test
    void restoringHistoricalRevisionCreatesNewRevisionWithoutMutatingHistory() {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        WorkspaceApiTest.Fixture fixture = new WorkspaceApiTest.Fixture();
        WorkspaceApi api = new WorkspaceApi(fixture.service(), () -> new AuthenticatedActor(tenant, actor));

        WorkspaceView workspace = api.createWorkspace(new CreateWorkspaceRequest("Team", ""));
        SpaceView space = api.createSpace(workspace.id(), new CreateSpaceRequest("General", "general"));
        PageView original = api.createPage(workspace.id(), space.id(),
                new CreatePageRequest(null, "Original", List.of(
                        new BlockRequest(null, "paragraph", "Original body", 0, Map.of()))));
        PageView edited = api.updatePage(original.id(),
                new UpdatePageRequest(original.revision(), "Edited", List.of(
                        new BlockRequest(null, "paragraph", "Edited body", 0, Map.of())), "edit"));

        PageView restored = api.restorePageRevision(original.id(),
                new RestorePageRevisionRequest(original.revision(), edited.revision()));

        assertEquals(3, restored.revision());
        assertEquals("Original", restored.title());
        assertEquals("Original body", restored.blocks().get(0).text());

        List<RevisionView> history = api.getPageHistory(original.id());
        assertEquals(List.of(1L, 2L, 3L), history.stream().map(RevisionView::revision).toList());
        assertEquals("Edited", history.get(1).title());
        assertEquals("Restored from revision 1", history.get(2).changeSummary());
    }

    @Test
    void restoringWithStaleCurrentRevisionReturnsConflict() {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        WorkspaceApi api = new WorkspaceApi(new WorkspaceApiTest.Fixture().service(),
                () -> new AuthenticatedActor(tenant, actor));

        WorkspaceView workspace = api.createWorkspace(new CreateWorkspaceRequest("Team", ""));
        SpaceView space = api.createSpace(workspace.id(), new CreateSpaceRequest("General", "general"));
        PageView original = api.createPage(workspace.id(), space.id(), new CreatePageRequest(null, "Original", List.of()));
        api.updatePage(original.id(), new UpdatePageRequest(1, "Edited", List.of(), "edit"));

        ApiException conflict = assertThrows(ApiException.class, () -> api.restorePageRevision(original.id(),
                new RestorePageRevisionRequest(1, 1)));
        assertEquals(409, conflict.status());
    }

    @Test
    void restoringUnknownRevisionIsRejected() {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        WorkspaceApi api = new WorkspaceApi(new WorkspaceApiTest.Fixture().service(),
                () -> new AuthenticatedActor(tenant, actor));

        WorkspaceView workspace = api.createWorkspace(new CreateWorkspaceRequest("Team", ""));
        SpaceView space = api.createSpace(workspace.id(), new CreateSpaceRequest("General", "general"));
        PageView page = api.createPage(workspace.id(), space.id(), new CreatePageRequest(null, "Original", List.of()));

        ApiException invalid = assertThrows(ApiException.class, () -> api.restorePageRevision(page.id(),
                new RestorePageRevisionRequest(99, page.revision())));
        assertEquals(400, invalid.status());
    }
}
