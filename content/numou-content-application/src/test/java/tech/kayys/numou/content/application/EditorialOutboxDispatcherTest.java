package tech.kayys.numou.content.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.editorial.EditorialReviewId;
import tech.kayys.numou.content.spi.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorialOutboxDispatcherTest {
    static final class Outbox implements EditorialOutboxRepository {
        final EditorialOutboxMessage message; boolean acknowledged; Instant retryAt; String error;
        Outbox(EditorialOutboxMessage message){this.message=message;}
        public List<EditorialOutboxMessage> claim(UUID worker,Instant now,Instant lease,int limit){return List.of(message);}
        public boolean markPublished(TenantId tenant,UUID event,UUID worker,Instant at){acknowledged=true;return true;}
        public boolean releaseForRetry(TenantId tenant,UUID event,UUID worker,Instant at,String failure){retryAt=at;error=failure;return true;}
    }
    static final class Workflow implements EditorialWorkflowOrchestrator {
        int starts,signals,completes,cancels; boolean fail;
        public void start(TenantId t,UUID event,ContentId c,int revision,EditorialReviewId review){starts++;if(fail)throw new IllegalStateException("offline");}
        public void signal(TenantId t,UUID event,EditorialReviewId review,String type,String payload){signals++;}
        public void complete(TenantId t,UUID event,EditorialReviewId review){completes++;}
        public void cancel(TenantId t,UUID event,EditorialReviewId review){cancels++;}
    }
    private EditorialOutboxMessage message(String type){
        return new EditorialOutboxMessage(new TenantId(UUID.randomUUID()),UUID.randomUUID(),EditorialReviewId.newId(),
                ContentId.newId(),1,type,"{}",1,Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test void dispatchesAfterClaimAndAcknowledgesSuccessfulWorkflowCall(){
        var outbox=new Outbox(message("REVIEW_SUBMITTED"));var workflow=new Workflow();
        var dispatcher=new EditorialOutboxDispatcher(outbox,workflow,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC),UUID.randomUUID());
        assertEquals(1,dispatcher.dispatchBatch(10));assertEquals(1,workflow.starts);
        assertTrue(outbox.acknowledged);assertNull(outbox.retryAt);
    }

    @Test void failureReleasesLeaseWithBackoffAndDoesNotAcknowledge(){
        var outbox=new Outbox(message("REVIEW_SUBMITTED"));var workflow=new Workflow();workflow.fail=true;
        var dispatcher=new EditorialOutboxDispatcher(outbox,workflow,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC),UUID.randomUUID());
        assertEquals(0,dispatcher.dispatchBatch(10));assertFalse(outbox.acknowledged);
        assertEquals(Instant.parse("2026-01-01T00:00:30Z"),outbox.retryAt);
        assertTrue(outbox.error.contains("offline"));
    }

    @Test void rejectsInvalidBatchLimits(){
        var dispatcher=new EditorialOutboxDispatcher(new Outbox(message("REVIEW_SUBMITTED")),new Workflow(),
                Clock.systemUTC(),UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->dispatcher.dispatchBatch(0));
        assertThrows(IllegalArgumentException.class,()->dispatcher.dispatchBatch(501));
    }
}
