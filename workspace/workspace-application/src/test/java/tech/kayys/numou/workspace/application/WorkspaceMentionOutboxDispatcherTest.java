package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDeliveryPort;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMentionOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test void successfulDeliveryIsAcknowledgedWithTheClaimLeaseToken() {
        FakeStore store = new FakeStore(1);
        WorkspaceMentionOutboxDispatcher dispatcher = new WorkspaceMentionOutboxDispatcher(
                store, mention -> assertEquals("workspace:comment-mention:" + mention.commentId() + ":" + mention.recipientId(),
                        mention.idempotencyKey()),
                Clock.fixed(NOW, ZoneOffset.UTC), "worker-a", 3, Duration.ofSeconds(30));

        var result = dispatcher.dispatch(10);

        assertEquals(new WorkspaceMentionOutboxDispatcher.DispatchResult(1, 1, 0, 0), result);
        assertEquals(store.claimed.leaseToken(), store.completedToken);
    }

    @Test void transientFailureSchedulesRetryAndFinalAttemptDeadLetters() {
        FakeStore first = new FakeStore(1);
        WorkspaceMentionOutboxDispatcher retrying = new WorkspaceMentionOutboxDispatcher(
                first, mention -> { throw new IllegalStateException("notification unavailable"); },
                Clock.fixed(NOW, ZoneOffset.UTC), "worker-a", 3, Duration.ofSeconds(30));
        var retryResult = retrying.dispatch(1);
        assertEquals(1, retryResult.retried());
        assertTrue(first.retryAt.isAfter(NOW));
        assertEquals(first.claimed.leaseToken(), first.completedToken);

        FakeStore last = new FakeStore(3);
        WorkspaceMentionOutboxDispatcher terminal = new WorkspaceMentionOutboxDispatcher(
                last, mention -> { throw new IllegalStateException("still unavailable"); },
                Clock.fixed(NOW, ZoneOffset.UTC), "worker-a", 3, Duration.ofSeconds(30));
        var deadResult = terminal.dispatch(1);
        assertEquals(1, deadResult.deadLettered());
        assertEquals(last.claimed.leaseToken(), last.completedToken);
    }

    private static final class FakeStore implements WorkspaceMentionOutboxStore {
        private final ClaimedMention claimed;
        private UUID completedToken;
        private Instant retryAt;
        FakeStore(int attempts) {
            UUID tenant=UUID.randomUUID(), page=UUID.randomUUID(), thread=UUID.randomUUID();
            UUID comment=UUID.randomUUID(), recipient=UUID.randomUUID(), actor=UUID.randomUUID();
            UUID id=UUID.randomUUID(), token=UUID.randomUUID();
            claimed=new ClaimedMention(id,tenant,page,thread,comment,recipient,actor,"member","Member",
                    "Page","workspace:comment-mention:"+comment+":"+recipient,attempts,attempts,token,NOW.plusSeconds(30));
        }
        public boolean createThreadWithMentions(tech.kayys.numou.workspace.domain.PageCommentThread t,
                tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry a,List<MentionIntent> m){throw new UnsupportedOperationException();}
        public boolean appendCommentWithMentions(UUID t,UUID p,UUID th,tech.kayys.numou.workspace.domain.PageComment c,
                tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry a,List<MentionIntent> m){throw new UnsupportedOperationException();}
        public List<ClaimedMention> claimDue(String worker,Instant now,Instant until,int limit){return List.of(claimed);}
        public boolean markDelivered(UUID tenant,UUID id,UUID token,Instant now){completedToken=token;return true;}
        public boolean scheduleRetry(UUID tenant,UUID id,UUID token,Instant at,String error,Instant now){completedToken=token;retryAt=at;return true;}
        public boolean markDead(UUID tenant,UUID id,UUID token,String error,Instant now){completedToken=token;return true;}
        public List<WorkspaceMentionOutboxStore.DeadMention> listDead(UUID tenant,int limit){return List.of();}
        public boolean requeueDead(UUID tenant,UUID id,UUID actor,Instant now){return true;}
    }
}
