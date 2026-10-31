package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PageCommentServiceTest {
    @Test
    void blockAnchorsMustBelongToTheTargetPageAndSurviveThreadLifecycle() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Page page = new Page(UUID.randomUUID(), tenant, UUID.randomUUID(), UUID.randomUUID(), null,
                "Anchored", List.of(new PageBlock(blockId, BlockType.PARAGRAPH, "Review me", 0, Map.of())),
                1, Instant.EPOCH, Instant.EPOCH);
        Store store = new Store(page);
        PageCommentService service = new PageCommentService(store, store,
                (t, a, resource, id, action) -> {}, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        PageCommentThread thread = service.createThread(tenant, actor, page.id(), blockId, "Review this block");
        assertEquals(blockId, thread.anchorBlockId());
        assertEquals(blockId, thread.resolve(actor, Instant.EPOCH.plusSeconds(1)).anchorBlockId());
        assertThrows(IllegalArgumentException.class,
                () -> service.createThread(tenant, actor, page.id(), UUID.randomUUID(), "Wrong block"));
        assertNull(service.createThread(tenant, actor, page.id(), "Page-level discussion").anchorBlockId());
    }

    @Test
    void createsThreadRepliesAndResolvesWithRevisionGuard() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Page page = page(tenant);
        Store store = new Store(page);
        PageCommentService service = new PageCommentService(store, store,
                (t, a, resource, id, action) -> {}, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        PageCommentThread created = service.createThread(tenant, actor, page.id(), "  Please review this section.  ");
        assertEquals(1, created.comments().size());
        assertEquals("Please review this section.", created.comments().get(0).body());
        assertEquals("PAGE_COMMENT_THREAD_CREATED", store.audits.get(0).action());

        PageCommentThread replied = service.reply(tenant, UUID.randomUUID(), page.id(), created.id(),
                created.comments().get(0).id(), "I have added the missing context.");
        assertEquals(2, replied.comments().size());
        assertEquals(created.comments().get(0).id(), replied.comments().get(1).parentCommentId());
        assertEquals("PAGE_COMMENT_REPLIED", store.audits.get(1).action());

        PageCommentThread resolved = service.resolve(tenant, actor, page.id(), created.id(), 1);
        assertTrue(resolved.resolved());
        assertEquals(2, resolved.revision());
        assertEquals(actor, resolved.resolvedBy());
        assertThrows(WorkspaceService.RevisionConflictException.class,
                () -> service.resolve(tenant, actor, page.id(), created.id(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.reply(tenant, actor, page.id(), created.id(), created.comments().get(0).id(), "Late reply"));

        PageCommentThread reopened = service.reopen(tenant, actor, page.id(), created.id(), 2);
        assertFalse(reopened.resolved());
        assertEquals(3, reopened.revision());
        assertNull(reopened.resolvedBy());
        assertEquals(4, store.audits.size());
    }

    @Test
    void tenantAndPageScopeAreEnforcedBeforeCommentReadsAndWrites() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Page page = page(tenant);
        Store store = new Store(page);
        List<String> checks = new ArrayList<>();
        PageCommentService service = new PageCommentService(store, store,
                (t, a, resource, id, action) -> checks.add(t + ":" + id + ":" + action),
                Clock.systemUTC());

        assertThrows(IllegalArgumentException.class,
                () -> service.listThreads(UUID.randomUUID(), actor, page.id()));
        assertThrows(IllegalArgumentException.class,
                () -> service.createThread(tenant, actor, UUID.randomUUID(), "No page"));
        assertTrue(checks.isEmpty(), "missing resources must not reach authorization or mutation");
        service.listThreads(tenant, actor, page.id());
        assertTrue(checks.get(0).endsWith(":READ"));
    }

    @Test
    void rejectsBlankOversizedBodiesAndRepliesToForeignComments() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Page page = page(tenant), other = page(tenant);
        Store store = new Store(page, other);
        PageCommentService service = new PageCommentService(store, store,
                (t, a, resource, id, action) -> {}, Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> service.createThread(tenant, actor, page.id(), "  "));
        assertThrows(IllegalArgumentException.class, () -> service.createThread(tenant, actor, page.id(), "x".repeat(10_001)));
        PageCommentThread thread = service.createThread(tenant, actor, page.id(), "Question");
        PageCommentThread otherThread = service.createThread(tenant, actor, other.id(), "Other question");
        assertThrows(IllegalArgumentException.class, () -> service.reply(tenant, actor, page.id(),
                thread.id(), otherThread.comments().get(0).id(), "Cross-thread reply"));
    }

    private static Page page(UUID tenant) {
        return new Page(UUID.randomUUID(), tenant, UUID.randomUUID(), UUID.randomUUID(),
                null, "Page", List.of(), 1, Instant.EPOCH, Instant.EPOCH);
    }

    private static final class Store implements PageRepository, AuditedPageCommentRepository {
        private final Map<UUID, Page> pages = new HashMap<>();
        private final Map<UUID, PageCommentThread> threads = new LinkedHashMap<>();
        private final List<AuditEntry> audits = new ArrayList<>();

        Store(Page... values) { for (Page page : values) pages.put(page.id(), page); }
        public Optional<Page> findPage(UUID tenant, UUID id) {
            return Optional.ofNullable(pages.get(id)).filter(page -> page.tenantId().equals(tenant));
        }
        public List<Page> findChildren(UUID tenant, UUID space, UUID parent) { return List.of(); }
        public boolean saveWithRevision(Page page, PageRevision revision, long expected) { return false; }
        public List<PageRevision> history(UUID tenant, UUID page) { return List.of(); }
        public Optional<PageCommentThread> findThread(UUID tenant, UUID page, UUID id) {
            return Optional.ofNullable(threads.get(id))
                    .filter(thread -> thread.tenantId().equals(tenant) && thread.pageId().equals(page));
        }
        public List<PageCommentThread> findThreads(UUID tenant, UUID page) {
            return threads.values().stream().filter(thread -> thread.tenantId().equals(tenant)
                    && thread.pageId().equals(page)).toList();
        }
        public boolean createThread(PageCommentThread thread, AuditEntry audit) {
            if (threads.containsKey(thread.id())) return false;
            threads.put(thread.id(), thread); audits.add(audit); return true;
        }
        public boolean appendComment(UUID tenant, UUID page, UUID threadId, PageComment comment, AuditEntry audit) {
            PageCommentThread thread = findThread(tenant, page, threadId).orElse(null);
            if (thread == null || thread.resolved()
                    || thread.comments().stream().noneMatch(entry -> entry.id().equals(comment.parentCommentId()))) return false;
            List<PageComment> entries = new ArrayList<>(thread.comments()); entries.add(comment);
            threads.put(threadId, thread.withComments(entries)); audits.add(audit); return true;
        }
        public boolean updateThreadResolution(PageCommentThread changed, long expected, AuditEntry audit) {
            PageCommentThread current = findThread(changed.tenantId(), changed.pageId(), changed.id()).orElse(null);
            if (current == null || current.revision() != expected) return false;
            threads.put(changed.id(), changed); audits.add(audit); return true;
        }
    }
}
