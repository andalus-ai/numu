package tech.kayys.numou.content.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.editorial.*;
import tech.kayys.numou.content.domain.model.ContentTypeId;
import tech.kayys.numou.content.spi.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorialReviewApplicationServiceTest {
    static final class Entries implements ContentRepository {
        final ContentEntry entry;
        Entries(ContentEntry entry){this.entry=entry;}
        public void create(ContentEntry e){throw new UnsupportedOperationException();}
        public Optional<ContentEntry> find(TenantId t,ContentId id){return entry.tenantId().equals(t)&&entry.id().equals(id)?Optional.of(entry):Optional.empty();}
        public boolean appendRevision(ContentEntry e,int expected){return false;}
        public boolean transitionStatus(TenantId t,ContentId id,ContentStatus expected,ContentStatus target,Instant at){return false;}
    }
    static final class Reviews implements EditorialReviewRepository {
        EditorialReview value;
        public void submit(EditorialReview review){value=review;}
        public Optional<EditorialReview> find(TenantId t,EditorialReviewId id){return value!=null&&value.tenantId().equals(t)&&value.id().equals(id)?Optional.of(value):Optional.empty();}
        public Optional<EditorialReview> findActiveForRevision(TenantId t,ContentId id,int revision){return Optional.ofNullable(value).filter(v->v.tenantId().equals(t)&&v.contentId().equals(id)&&v.revisionNumber()==revision&&v.status()==EditorialReview.Status.IN_REVIEW);}
        public boolean update(EditorialReview review,long expected){if(value==null||value.version()!=expected)return false;value=review;return true;}
    }
    static final class Authorization implements EditorialAuthorizationPort {
        int submit,review,comment,cancel,eligible;
        public void requireSubmit(TenantId t,ActorId a,ContentId c){submit++;}
        public void requireReview(TenantId t,ActorId a,ContentId c){review++;}
        public void requireComment(TenantId t,ActorId a,ContentId c){comment++;}
        public void requireCancel(TenantId t,ActorId a,ContentId c){cancel++;}
        public void requireReviewerEligible(TenantId t,ActorId a,UUID reviewer){eligible++;}
    }

    @Test void submitDecisionAndCommentUseTrustedActorAndPersistVersionedAggregate(){
        TenantId tenant=new TenantId(UUID.randomUUID());ActorId actor=new ActorId(UUID.randomUUID());
        ContentEntry entry=ContentEntry.create(tenant,"Article","article","{}",ContentTypeId.newId(),1,actor,Instant.parse("2026-01-01T00:00:00Z"));
        Entries entries=new Entries(entry);Reviews reviews=new Reviews();Authorization auth=new Authorization();
        Clock clock=Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"),ZoneOffset.UTC);
        EditorialReviewApplicationService service=new EditorialReviewApplicationService(entries,reviews,auth,clock);
        ContentCommandContext context=new ContentCommandContext(tenant,actor);
        EditorialReviewPolicy policy=new EditorialReviewPolicy(EditorialReviewPolicy.Mode.SINGLE,List.of(actor.value()),1);

        EditorialReview submitted=service.submit(context,entry.id(),policy);
        assertEquals(1,auth.submit);assertEquals(1,auth.eligible);
        assertEquals(ContentStatus.DRAFT,entry.status());
        assertEquals(1,submitted.revisionNumber());

        EditorialReview commented=service.comment(context,submitted.id(),UUID.randomUUID(),"Please add source");
        assertEquals(1,auth.comment);assertEquals(1,commented.comments().size());
        EditorialReview approved=service.decide(context,submitted.id(),EditorialReview.Decision.APPROVE,null);
        assertEquals(1,auth.review);assertEquals(EditorialReview.Status.APPROVED,approved.status());
        assertEquals(2,approved.version());
        assertEquals(approved,service.get(context,submitted.id()));
    }

    @Test void submitRejectsContentThatIsNotDraftBeforePersistingReview(){
        TenantId tenant=new TenantId(UUID.randomUUID());ActorId actor=new ActorId(UUID.randomUUID());
        ContentEntry draft=ContentEntry.create(tenant,"Article","article","{}",ContentTypeId.newId(),1,actor,Instant.now());
        ContentEntry reviewing=draft.transitionTo(ContentStatus.IN_REVIEW,Instant.now());
        Reviews reviews=new Reviews();Authorization auth=new Authorization();
        var service=new EditorialReviewApplicationService(new Entries(reviewing),reviews,auth,Clock.systemUTC());
        var policy=new EditorialReviewPolicy(EditorialReviewPolicy.Mode.SINGLE,List.of(actor.value()),1);
        assertThrows(IllegalStateException.class,()->service.submit(new ContentCommandContext(tenant,actor),draft.id(),policy));
        assertNull(reviews.value);
    }
}
