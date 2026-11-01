package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxAdminPolicy;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore;
import tech.kayys.numou.workspace.domain.PageComment;
import tech.kayys.numou.workspace.domain.PageCommentThread;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMentionOutboxOperationsServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void deadLetterReadsAreTenantScopedAndRequireOperationalAuthorization() {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        FakeStore store = new FakeStore();
        store.deadTenant = tenant;
        store.dead = List.of(new WorkspaceMentionOutboxStore.DeadMention(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                4, "notification unavailable", NOW, NOW));
        RecordingPolicy policy = new RecordingPolicy();
        var service = service(store, policy);

        assertEquals(store.dead, service.listDead(tenant, actor, 20));
        assertEquals(tenant, store.listTenant);
        assertEquals(tenant, policy.tenant);
        assertEquals(actor, policy.actor);

        assertThrows(IllegalArgumentException.class, () -> service.listDead(tenant, actor, 201));
        assertThrows(NullPointerException.class, () -> service.listDead(otherTenant, null, 20));
    }

    @Test
    void authorizationRunsBeforeAnyDeadLetterMutation() {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        FakeStore store = new FakeStore();
        RecordingPolicy policy = new RecordingPolicy();
        policy.deny = true;
        var service = service(store, policy);

        assertThrows(SecurityException.class, () -> service.requeueDead(tenant, actor, delivery));
        assertFalse(store.requeued);
        assertEquals(tenant, policy.tenant);
        assertEquals(actor, policy.actor);
    }

    @Test
    void requeueUsesTrustedActorAndClockAndDoesNotRevealForeignRows() {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        FakeStore store = new FakeStore();
        store.requeueResult = false;
        var service = service(store, new RecordingPolicy());

        assertFalse(service.requeueDead(tenant, actor, delivery));
        assertTrue(store.requeued);
        assertEquals(tenant, store.requeueTenant);
        assertEquals(delivery, store.requeueId);
        assertEquals(actor, store.requeueActor);
        assertEquals(NOW, store.requeueAt);
    }

    private static WorkspaceMentionOutboxOperationsService service(
            FakeStore store, WorkspaceMentionOutboxAdminPolicy policy) {
        return new WorkspaceMentionOutboxOperationsService(
                store, policy, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class RecordingPolicy implements WorkspaceMentionOutboxAdminPolicy {
        UUID tenant;
        UUID actor;
        boolean deny;
        @Override public void requireManage(UUID tenantId, UUID actorId) {
            tenant = tenantId;
            actor = actorId;
            if (deny) throw new SecurityException("operational access denied");
        }
    }

    private static final class FakeStore implements WorkspaceMentionOutboxStore {
        UUID deadTenant;
        UUID listTenant;
        List<DeadMention> dead = List.of();
        boolean requeued;
        boolean requeueResult = true;
        UUID requeueTenant;
        UUID requeueId;
        UUID requeueActor;
        Instant requeueAt;

        @Override public List<DeadMention> listDead(UUID tenantId, int limit) {
            listTenant = tenantId;
            return tenantId.equals(deadTenant) ? dead : List.of();
        }
        @Override public boolean requeueDead(UUID tenantId, UUID deliveryId, UUID actorId, Instant now) {
            requeued = true;
            requeueTenant = tenantId;
            requeueId = deliveryId;
            requeueActor = actorId;
            requeueAt = now;
            return requeueResult;
        }
        @Override public boolean createThreadWithMentions(PageCommentThread thread, AuditEntry audit,
                                                           List<MentionIntent> mentions) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean appendCommentWithMentions(UUID tenantId, UUID pageId, UUID threadId,
                PageComment comment, AuditEntry audit, List<MentionIntent> mentions) {
            throw new UnsupportedOperationException();
        }
        @Override public List<ClaimedMention> claimDue(String workerId, Instant now, Instant leaseUntil, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean markDelivered(UUID tenantId, UUID deliveryId, UUID leaseToken, Instant now) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean scheduleRetry(UUID tenantId, UUID deliveryId, UUID leaseToken,
                                               Instant availableAt, String error, Instant now) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean markDead(UUID tenantId, UUID deliveryId, UUID leaseToken, String error, Instant now) {
            throw new UnsupportedOperationException();
        }
    }
}
