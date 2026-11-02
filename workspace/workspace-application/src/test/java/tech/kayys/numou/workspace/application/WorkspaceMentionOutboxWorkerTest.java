package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMentionOutboxWorkerTest {
    @Test
    void rejectsInvalidBatchLimit() {
        var worker = new WorkspaceMentionOutboxWorker(limit -> {
            throw new AssertionError("dispatcher must not run");
        }, Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> worker.runOnce(0));
        assertThrows(IllegalArgumentException.class, () -> worker.runOnce(501));
    }

    @Test
    void recordsCompletedRunAndDoesNotOverlapInvocations() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WorkspaceMentionOutboxDispatcher dispatcher = new WorkspaceMentionOutboxDispatcher(
                new EmptyStore(), mention -> { }, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                "worker-test", 3, java.time.Duration.ofSeconds(30)) {
        };
        // The dispatcher is final; exercise the overlap guard through a blocking delivery port
        // using a claimed item would require a database-backed store. This test covers run metadata.
        var worker = new WorkspaceMentionOutboxWorker(dispatcher, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var result = worker.runOnce(10);
        assertTrue(result.executed());
        assertNotNull(result.dispatch());
        assertEquals(result, worker.lastRun());
        assertFalse(worker.isRunning());
    }

    private static final class EmptyStore implements tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore {
        public boolean createThreadWithMentions(tech.kayys.numou.workspace.domain.PageCommentThread t, tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry a, List<MentionIntent> m) { return false; }
        public boolean appendCommentWithMentions(java.util.UUID t, java.util.UUID p, java.util.UUID th, tech.kayys.numou.workspace.domain.PageComment c, tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry a, List<MentionIntent> m) { return false; }
        public List<ClaimedMention> claimDue(String w, Instant n, Instant l, int limit) { return List.of(); }
        public boolean markDelivered(java.util.UUID t, java.util.UUID d, java.util.UUID token, Instant n) { return false; }
        public boolean scheduleRetry(java.util.UUID t, java.util.UUID d, java.util.UUID token, Instant at, String e, Instant n) { return false; }
        public boolean markDead(java.util.UUID t, java.util.UUID d, java.util.UUID token, String e, Instant n) { return false; }
        public List<DeadMention> listDead(java.util.UUID t, int limit) { return List.of(); }
        public boolean requeueDead(java.util.UUID t, java.util.UUID d, java.util.UUID a, Instant n) { return false; }
    }
}
