package tech.kayys.numou.workspace.adapter.rest;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.NotificationService;
import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionNotificationPort.MentionNotification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceNotificationBridgeTest {
    @Test
    void createsRecipientScopedIdempotentInboxNotificationWithoutProviderCoupling() {
        UUID tenant=UUID.randomUUID(), recipient=UUID.randomUUID(), actor=UUID.randomUUID();
        UUID page=UUID.randomUUID(), comment=UUID.randomUUID();
        Map<String,Notification> stored=new HashMap<>();
        NotificationRepository repository=new NotificationRepository(){
            public Notification createIfAbsent(Notification value){
                return stored.computeIfAbsent(value.tenantId()+":"+value.recipientId()+":"+value.idempotencyKey(),k->value);
            }
            public List<Notification> findInbox(UUID t,UUID r,int l,Instant before){return List.of();}
            public Optional<Notification> findById(UUID t,UUID r,UUID id){return stored.values().stream().filter(n->n.tenantId().equals(t)&&n.recipientId().equals(r)&&n.id().equals(id)).findFirst();}
            public Optional<Notification> markRead(UUID t,UUID r,UUID id,Instant at){return Optional.empty();}
        };
        NotificationService service=new NotificationService(repository,Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"),ZoneOffset.UTC));
        WorkspaceNotificationBridge bridge=new WorkspaceNotificationBridge(service);
        MentionNotification mention=new MentionNotification(tenant,recipient,actor,page,comment,
                "amina","Project Lead","Operations roadmap","workspace:comment-mention:"+comment+":"+recipient);
        bridge.notifyMention(mention);
        bridge.notifyMention(mention);
        assertEquals(1,stored.size());
        Notification result=stored.values().iterator().next();
        assertEquals(tenant,result.tenantId());
        assertEquals(recipient,result.recipientId());
        assertEquals("workspace.mention",result.type());
        assertEquals("Project Lead mentioned you",result.title());
        assertTrue(result.body().contains("Operations roadmap"));
        assertTrue(result.sourceReference().contains(comment.toString()));
    }
}
