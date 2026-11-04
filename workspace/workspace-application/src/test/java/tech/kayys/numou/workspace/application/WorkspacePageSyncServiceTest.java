package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspacePageSyncServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T00:00:00Z");

    @Test void returnsOrderedBoundedRevisionsAndResumableCursor() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Page page = page(tenant, 5);
        WorkspacePageSyncService service = service(tenant, page, revisions(tenant, page.id(), 5),
                (t, a, r, id, action) -> {
                    assertEquals(tenant, t); assertEquals(actor, a);
                    assertEquals(Resource.PAGE, r); assertEquals(page.id(), id); assertEquals(Action.READ, action);
                });
        var first = service.changes(tenant, actor, page.id(), 0, 2);
        assertEquals(5, first.currentRevision());
        assertEquals(0, first.requestedCursor());
        assertEquals(2, first.nextCursor());
        assertTrue(first.hasMore());
        assertEquals(List.of(1L, 2L), first.revisions().stream().map(PageRevision::revision).toList());
        var second = service.changes(tenant, actor, page.id(), first.nextCursor(), 2);
        assertEquals(4, second.nextCursor());
        assertTrue(second.hasMore());
        assertEquals(List.of(3L, 4L), second.revisions().stream().map(PageRevision::revision).toList());
        var last = service.changes(tenant, actor, page.id(), second.nextCursor(), 2);
        assertEquals(5, last.nextCursor());
        assertFalse(last.hasMore());
        assertEquals(List.of(5L), last.revisions().stream().map(PageRevision::revision).toList());
    }

    @Test void authorizationPrecedesRevisionHistoryRead() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Page page = page(tenant, 1);
        WorkspacePageSyncService service = service(tenant, page, revisions(tenant, page.id(), 1),
                (t, a, r, id, action) -> { throw new SecurityException("denied"); });
        assertThrows(SecurityException.class, () -> service.changes(tenant, actor, page.id(), 0, 10));
    }

    @Test void rejectsForeignTenantAndFutureOrInvalidCursors() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Page page = page(tenant, 2);
        WorkspacePageSyncService service = service(tenant, page, revisions(tenant, page.id(), 2),
                (t, a, r, id, action) -> {});
        assertThrows(IllegalArgumentException.class, () -> service.changes(UUID.randomUUID(), actor, page.id(), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> service.changes(tenant, actor, page.id(), 3, 10));
        assertThrows(IllegalArgumentException.class, () -> service.changes(tenant, actor, page.id(), -1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.changes(tenant, actor, page.id(), 0, 101));
    }

    private static WorkspacePageSyncService service(UUID tenant, Page page, List<PageRevision> history, AccessPolicy access) {
        PageRepository repository = new PageRepository() {
            public Optional<Page> findPage(UUID t, UUID id) {
                return t.equals(tenant) && page.id().equals(id) ? Optional.of(page) : Optional.empty();
            }
            public List<Page> findChildren(UUID t, UUID s, UUID p) { return List.of(); }
            public boolean saveWithRevision(Page p, PageRevision r, long expected) { return false; }
            public List<PageRevision> history(UUID t, UUID id) {
                return t.equals(tenant) && page.id().equals(id) ? history : List.of();
            }
        };
        return new WorkspacePageSyncService(repository, access);
    }

    private static Page page(UUID tenant, long revision) {
        return new Page(UUID.randomUUID(), tenant, UUID.randomUUID(), UUID.randomUUID(), null,
                "Page", List.of(), revision, NOW, NOW);
    }
    private static List<PageRevision> revisions(UUID tenant, UUID page, int count) {
        List<PageRevision> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) result.add(new PageRevision(
                tenant, page, i, "Revision " + i, List.of(), UUID.randomUUID(),
                NOW.plusSeconds(i), "change " + i));
        return List.copyOf(result);
    }
}
