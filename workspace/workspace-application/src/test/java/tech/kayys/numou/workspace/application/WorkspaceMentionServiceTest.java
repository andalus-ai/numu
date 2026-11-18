package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDirectory;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDirectory.Member;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionNotificationPort;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore;
import tech.kayys.numou.workspace.domain.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMentionServiceTest {
    @Test
    void resolvesOnlyActiveTenantMembersDeduplicatesAndExcludesAuthor() {
        UUID tenant=UUID.randomUUID(), actor=UUID.randomUUID(), pageId=UUID.randomUUID();
        Page page=new Page(pageId,tenant,UUID.randomUUID(),UUID.randomUUID(),null,"Roadmap",List.of(),1,Instant.EPOCH,Instant.EPOCH);
        Member author=new Member(actor,"author","Author");
        Member amina=new Member(UUID.randomUUID(),"amina","Amina");
        Member reviewer=new Member(UUID.randomUUID(),"reviewer","Reviewer");
        List<WorkspaceMentionNotificationPort.MentionNotification> sent=new ArrayList<>();
        WorkspaceMentionDirectory directory=new WorkspaceMentionDirectory(){
            public Optional<Member> findActiveByHandle(UUID t,String h){
                if(!tenant.equals(t))return Optional.empty();
                return switch(h){case "author"->Optional.of(author);case "amina"->Optional.of(amina);case "reviewer"->Optional.of(reviewer);default->Optional.empty();};
            }
            public Optional<Member> findActiveByUserId(UUID t,UUID id){return tenant.equals(t)&&id.equals(actor)?Optional.of(author):Optional.empty();}
            public List<Member> searchActive(UUID t,String q,int limit){return tenant.equals(t)?List.of(amina,reviewer).stream().filter(m->m.handle().startsWith(q)).limit(limit).toList():List.of();}
        };
        PageRepository pages=new PageRepository(){
            public Optional<Page> findPage(UUID t,UUID id){return tenant.equals(t)&&pageId.equals(id)?Optional.of(page):Optional.empty();}
            public List<Page> findChildren(UUID t,UUID s,UUID p){return List.of();}
            public boolean saveWithRevision(Page p,PageRevision r,long e){return false;}
            public List<PageRevision> history(UUID t,UUID p){return List.of();}
        };
        WorkspaceMentionService service=new WorkspaceMentionService(pages,(t,a,r,id,action)->{},
                directory,sent::add);
        UUID comment=UUID.randomUUID();
        List<UUID> notified=service.notifyCommentMentions(tenant,actor,pageId,comment,
                "Thanks @amina, @AMINA and @reviewer. @author is me; @unknown is not in this tenant.");
        assertEquals(List.of(amina.userId(),reviewer.userId()),notified);
        assertEquals(2,sent.size());
        assertEquals("Author",sent.get(0).actorDisplayName());
        assertEquals("workspace:comment-mention:"+comment+":"+amina.userId(),sent.get(0).idempotencyKey());
    }

    @Test
    void suggestionsRequirePageReadAccessAndNeverReturnTheCurrentActor() {
        UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),pageId=UUID.randomUUID();
        Page page=new Page(pageId,tenant,UUID.randomUUID(),UUID.randomUUID(),null,"Page",List.of(),1,Instant.EPOCH,Instant.EPOCH);
        Member self=new Member(actor,"self","Self"), other=new Member(UUID.randomUUID(),"sami","Sami");
        WorkspaceMentionDirectory directory=new WorkspaceMentionDirectory(){
            public Optional<Member> findActiveByHandle(UUID t,String h){return Optional.empty();}
            public Optional<Member> findActiveByUserId(UUID t,UUID id){return Optional.empty();}
            public List<Member> searchActive(UUID t,String q,int limit){return List.of(self,other);}
        };
        PageRepository pages=new PageRepository(){
            public Optional<Page> findPage(UUID t,UUID id){return Optional.of(page);}
            public List<Page> findChildren(UUID t,UUID s,UUID p){return List.of();}
            public boolean saveWithRevision(Page p,PageRevision r,long e){return false;}
            public List<PageRevision> history(UUID t,UUID p){return List.of();}
        };
        List<Action> actions=new ArrayList<>();
        WorkspaceMentionService service=new WorkspaceMentionService(pages,(t,a,r,id,action)->actions.add(action),directory,n->{});
        assertEquals(List.of(other),service.suggest(tenant,actor,pageId,"@sa",8));
        assertEquals(List.of(Action.READ),actions);
    }

    @Test
    void preparesDurableMentionIntentsWithoutASynchronousNotificationAdapter() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), pageId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();
        Page page = new Page(pageId, tenant, UUID.randomUUID(), UUID.randomUUID(), null,
                "Roadmap", List.of(), 1, Instant.EPOCH, Instant.EPOCH);
        Member recipient = new Member(UUID.randomUUID(), "reviewer", "Reviewer");
        WorkspaceMentionDirectory directory = new WorkspaceMentionDirectory() {
            public Optional<Member> findActiveByHandle(UUID t, String handle) {
                return tenant.equals(t) && "reviewer".equals(handle) ? Optional.of(recipient) : Optional.empty();
            }
            public Optional<Member> findActiveByUserId(UUID t, UUID id) { return Optional.empty(); }
            public List<Member> searchActive(UUID t, String query, int limit) { return List.of(); }
        };
        PageRepository pages = new PageRepository() {
            public Optional<Page> findPage(UUID t, UUID id) {
                return tenant.equals(t) && pageId.equals(id) ? Optional.of(page) : Optional.empty();
            }
            public List<Page> findChildren(UUID t, UUID s, UUID p) { return List.of(); }
            public boolean saveWithRevision(Page p, PageRevision r, long expected) { return false; }
            public List<PageRevision> history(UUID t, UUID p) { return List.of(); }
        };
        WorkspaceMentionService service = new WorkspaceMentionService(
                pages, (t, a, r, id, action) -> {}, directory);
        PageComment comment = PageComment.root(tenant, pageId, threadId, actor,
                "Please review this, @reviewer", Instant.EPOCH);

        List<WorkspaceMentionOutboxStore.MentionIntent> intents =
                service.prepareCommentMentions(tenant, actor, pageId, comment);

        assertEquals(1, intents.size());
        assertEquals(recipient.userId(), intents.get(0).recipientId());
        assertEquals("workspace:comment-mention:" + comment.id() + ":" + recipient.userId(),
                intents.get(0).idempotencyKey());
        assertThrows(IllegalStateException.class,
                () -> service.notifyCommentMentions(tenant, actor, pageId, comment.id(), comment.body()));
    }
}
