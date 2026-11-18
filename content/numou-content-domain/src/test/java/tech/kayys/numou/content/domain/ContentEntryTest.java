package tech.kayys.numou.content.domain;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ContentEntryTest {
 private final TenantId tenant=new TenantId(UUID.randomUUID()); private final ActorId actor=new ActorId(UUID.randomUUID()); private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
 @Test void createsFirstDraftRevision(){var entry=ContentEntry.create(tenant,"Hello","hello","{}",actor,now);assertEquals(1,entry.currentRevisionNumber());assertEquals(ContentStatus.DRAFT,entry.status());}
 @Test void revisionsAreAppendOnlyAndOldSnapshotRemainsUnchanged(){var entry=ContentEntry.create(tenant,"One","one","{}",actor,now);var revised=entry.revise(1,"Two","two","{\"v\":2}",actor,now.plusSeconds(1));assertEquals("One",entry.currentRevision().title());assertEquals("Two",revised.currentRevision().title());assertEquals(2,revised.revisions().size());assertThrows(UnsupportedOperationException.class,()->revised.revisions().clear());}
 @Test void rejectsStaleExpectedRevision(){var entry=ContentEntry.create(tenant,"One","one","{}",actor,now);assertThrows(RevisionConflictException.class,()->entry.revise(0,"Two","two","{}",actor,now));}
 @Test void lifecycleSupportsEditorialFlowAndRestoration(){var entry=ContentEntry.create(tenant,"One","one","{}",actor,now);var review=entry.transitionTo(ContentStatus.IN_REVIEW,now.plusSeconds(1));var approved=review.transitionTo(ContentStatus.APPROVED,now.plusSeconds(2));var scheduled=approved.transitionTo(ContentStatus.SCHEDULED,now.plusSeconds(3));var published=scheduled.transitionTo(ContentStatus.PUBLISHED,now.plusSeconds(4));var unpublished=published.transitionTo(ContentStatus.UNPUBLISHED,now.plusSeconds(5));var archived=unpublished.archive(now.plusSeconds(6));var restored=archived.restore(now.plusSeconds(7));assertEquals(ContentStatus.DRAFT,restored.status());assertEquals(1,restored.currentRevisionNumber());assertEquals(ContentStatus.DRAFT,entry.status());}
 @Test void rejectsIllegalLifecycleTransitions(){var entry=ContentEntry.create(tenant,"One","one","{}",actor,now);assertThrows(IllegalStateException.class,()->entry.transitionTo(ContentStatus.PUBLISHED,now));assertThrows(IllegalStateException.class,()->entry.transitionTo(ContentStatus.APPROVED,now));}
 @Test void archivedContentCannotBeRevisedAndCanBeRestored(){var entry=ContentEntry.create(tenant,"One","one","{}",actor,now).archive(now.plusSeconds(1));assertThrows(IllegalStateException.class,()->entry.revise(1,"Two","two","{}",actor,now));assertEquals(ContentStatus.DRAFT,entry.restore(now.plusSeconds(2)).status());}
 @Test void rejectsBlankTitleAndSlug(){assertThrows(IllegalArgumentException.class,()->ContentEntry.create(tenant," ","slug","{}",actor,now));assertThrows(IllegalArgumentException.class,()->ContentEntry.create(tenant,"Title"," ","{}",actor,now));}
}
