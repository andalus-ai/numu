package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.domain.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PageTemplateServiceTest {

    @Test void templateCreationFailsClosedWithoutAtomicAuditCapability() {
        UUID tenant = UUID.randomUUID(), workspaceId = UUID.randomUUID(), actor = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, tenant, "W", "", Instant.EPOCH);
        List<PageTemplate> stored = new ArrayList<>();
        WorkspacePorts.WorkspaceRepository workspaces = new WorkspacePorts.WorkspaceRepository() {
            public boolean save(Workspace value) { return false; }
            public Optional<Workspace> findWorkspace(UUID t, UUID id) {
                return t.equals(tenant) && id.equals(workspaceId) ? Optional.of(workspace) : Optional.empty();
            }
        };
        WorkspacePorts.PageTemplateRepository templates = new WorkspacePorts.PageTemplateRepository() {
            public boolean save(PageTemplate value) { stored.add(value); return true; }
            public Optional<PageTemplate> findTemplate(UUID t, UUID id) { return Optional.empty(); }
            public List<PageTemplate> findByWorkspace(UUID t, UUID w) { return List.copyOf(stored); }
        };
        WorkspaceService pageService = unusedWorkspaceService();
        PageTemplateService service = new PageTemplateService(workspaces, templates, pageService,
                (t, a, r, id, action) -> {}, event -> {}, java.time.Clock.systemUTC());

        assertThrows(IllegalStateException.class,
                () -> service.create(tenant, actor, workspaceId, "Meeting", "Meeting notes", List.of()));
        assertTrue(stored.isEmpty(), "template creation must be rejected before mutating the adapter");
    }

    private static WorkspaceService unusedWorkspaceService() {
        WorkspacePorts.WorkspaceRepository workspaces = new WorkspacePorts.WorkspaceRepository() {
            public boolean save(Workspace value) { return true; }
            public Optional<Workspace> findWorkspace(UUID t, UUID id) { return Optional.empty(); }
        };
        WorkspacePorts.SpaceRepository spaces = new WorkspacePorts.SpaceRepository() {
            public boolean save(Space value) { return true; }
            public Optional<Space> find(UUID t, UUID w, UUID id) { return Optional.empty(); }
            public boolean slugExists(UUID t, UUID w, String slug) { return false; }
        };
        WorkspacePorts.PageRepository pages = new WorkspacePorts.PageRepository() {
            public Optional<Page> findPage(UUID t, UUID id) { return Optional.empty(); }
            public List<Page> findChildren(UUID t, UUID s, UUID parent) { return List.of(); }
            public boolean saveWithRevision(Page page, PageRevision revision, long expected) { return false; }
            public List<PageRevision> history(UUID t, UUID id) { return List.of(); }
        };
        return new WorkspaceService(workspaces, spaces, pages, (t, a, r, id, action) -> {},
                event -> {}, (t, a, id) -> id, java.time.Clock.systemUTC(), true);
    }

    @Test void templateInstantiationUsesFreshBlockIdsAndPreservesContent() {
        UUID tenant = UUID.randomUUID(), workspace = UUID.randomUUID(), actor = UUID.randomUUID();
        PageTemplate template = PageTemplate.create(tenant, workspace, actor, "Meeting", "Meeting notes",
                List.of(PageBlock.text(BlockType.HEADING, "Agenda", 0),
                        PageBlock.text(BlockType.TODO, "Capture actions", 1)), Instant.EPOCH);
        List<PageBlock> first = template.instantiateBlocks();
        List<PageBlock> second = template.instantiateBlocks();
        assertEquals(List.of("Agenda", "Capture actions"), first.stream().map(PageBlock::text).toList());
        assertEquals(List.of(0, 1), first.stream().map(PageBlock::position).toList());
        assertNotEquals(first.get(0).id(), second.get(0).id());
        assertNotEquals(first.get(0).id(), template.blocks().get(0).id());
    }

    @Test void rejectsDuplicatePositionsAndInvalidNames() {
        UUID t = UUID.randomUUID(), w = UUID.randomUUID(), a = UUID.randomUUID();
        PageBlock one = PageBlock.text(BlockType.PARAGRAPH, "one", 0);
        PageBlock two = PageBlock.text(BlockType.PARAGRAPH, "two", 0);
        assertThrows(IllegalArgumentException.class,
                () -> PageTemplate.create(t, w, a, "Template", "Title", List.of(one, two), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> PageTemplate.create(t, w, a, " ", "Title", List.of(), Instant.EPOCH));
    }
}
