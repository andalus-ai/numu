package tech.kayys.numou.content.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.model.ContentTypeId;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ContentEntryTest {
 private final TenantId tenant=new TenantId(UUID.randomUUID());
 private final ActorId actor=new ActorId(UUID.randomUUID());
 private final ContentTypeId type=ContentTypeId.newId();
 private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
 @Test void createsSchemaBoundFirstDraftRevision(){
  var entry=ContentEntry.create(tenant,"Hello","hello","{}",type,1,actor,now);
  assertEquals(1,entry.currentRevisionNumber());assertEquals(ContentStatus.DRAFT,entry.status());
  assertEquals(type,entry.currentRevision().contentTypeId());assertEquals(1,entry.currentRevision().schemaVersion());
 }
 @Test void revisionsAreAppendOnlyAndCaptureSchemaVersionPerSnapshot(){
  var entry=ContentEntry.create(tenant,"One","one","{}",type,1,actor,now);
  var revised=entry.revise(1,"Two","two","{v:2}",type,2,actor,now.plusSeconds(1));
  assertEquals("One",entry.currentRevision().title());assertEquals("Two",revised.currentRevision().title());
  assertEquals(1,entry.currentRevision().schemaVersion());assertEquals(2,revised.currentRevision().schemaVersion());
  assertEquals(2,revised.revisions().size());assertThrows(UnsupportedOperationException.class,()->revised.revisions().clear());
 }
 @Test void rejectsStaleExpectedRevision(){
  var entry=ContentEntry.create(tenant,"One","one","{}",type,1,actor,now);
  assertThrows(RevisionConflictException.class,()->entry.revise(0,"Two","two","{}",type,1,actor,now));
 }
 @Test void archivedContentCannotBeRevisedAndCanBeRestored(){
  var entry=ContentEntry.create(tenant,"One","one","{}",type,1,actor,now).archive(now.plusSeconds(1));
  assertThrows(IllegalStateException.class,()->entry.revise(1,"Two","two","{}",type,1,actor,now));
  assertEquals(ContentStatus.DRAFT,entry.restore(now.plusSeconds(2)).status());
 }
 @Test void rejectsBlankTitleAndSlug(){
  assertThrows(IllegalArgumentException.class,()->ContentEntry.create(tenant," ","slug","{}",type,1,actor,now));
  assertThrows(IllegalArgumentException.class,()->ContentEntry.create(tenant,"Title"," ","{}",type,1,actor,now));
 }
 @Test void rejectsCorruptCurrentRevisionPointer(){
  var entry=ContentEntry.create(tenant,"One","one","{}",type,1,actor,now);
  assertThrows(IllegalArgumentException.class,()->ContentEntry.rehydrate(entry.id(),tenant,entry.status(),entry.revisions(),entry.createdAt(),entry.updatedAt(),2));
 }
}
