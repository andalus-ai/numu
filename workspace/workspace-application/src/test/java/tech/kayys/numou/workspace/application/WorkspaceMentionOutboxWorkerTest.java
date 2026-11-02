package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMentionOutboxWorkerTest {
    @Test
    void rejectsInvalidBatchLimit() {
        var worker = new WorkspaceMentionOutboxWorker(dispatcher(), Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> worker.runOnce(0));
        assertThrows(IllegalArgumentException.class, () -> worker.runOnce(501));
    }

    @Test
    void recordsCompletedRun() {
        var worker = new WorkspaceMentionOutboxWorker(dispatcher(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        var result = worker.runOnce(10);

        assertTrue(result.executed());
        assertNotNull(result.dispatch());
        assertEquals(result, worker.lastRun());
        assertFalse(worker.isRunning());
    }

    private static WorkspaceMentionOutboxDispatcher dispatcher() {
        return new WorkspaceMentionOutboxDispatcher(new EmptyStore(), mention -> { },
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "worker-test", 3,
                java.time.Duration.ofSeconds(30));
    }

    private static final class EmptyStore implements WorkspaceMentionOutboxStore {
        public boolean createThreadWithMentions(tech.kayys.numou.workspace.domain.PageCommentThread t, tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry a, List<MentionIntent> m) { return false; }
        public boolean appendCommentWithMentions(UUID t, UUID p, UUID th, tech.kayys.numou.workspace.domain.PageComment c, tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry a, List<MentionIntent> m) { return false; }
        public List<ClaimedMention> claimDue(String w, Instant n, Instant l, int limit) { return List.of(); }
        public boolean markDelivered(UUID t, UUID d, UUID token, Instant n) { return false; }
        public boolean scheduleRetry(UUID t, UUID d, UUID token, Instant at, String e, Instant n) { return false; }
        public boolean markDead(UUID t, UUID d, UUID token, String e, Instant n) { return false; }
        public List<DeadMention> listDead(UUID t, int limit) { return List.of(); }
        public boolean requeueDead(UUID t, UUID d, UUID a, Instant n) { return false; }
    }
}
