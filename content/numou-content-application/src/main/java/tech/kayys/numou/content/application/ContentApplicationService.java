package tech.kayys.numou.content.application;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.model.ContentTypeId;
import tech.kayys.numou.content.domain.model.ContentValidationResult;
import tech.kayys.numou.content.spi.ContentPayloadCodec;
import tech.kayys.numou.content.spi.ContentRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
/** Typed authoring use cases. Schema validation precedes serialization and persistence. */
public final class ContentApplicationService {
 private final ContentRepository repository; private final ContentTypeApplicationService types;
 private final ContentPayloadCodec payloadCodec; private final Clock clock;
 public ContentApplicationService(ContentRepository repository,ContentTypeApplicationService types,ContentPayloadCodec payloadCodec,Clock clock){
  this.repository=Objects.requireNonNull(repository);this.types=Objects.requireNonNull(types);
  this.payloadCodec=Objects.requireNonNull(payloadCodec);this.clock=Objects.requireNonNull(clock);
 }
 public ContentEntry create(ContentCommandContext context,ContentTypeId typeId,int schemaVersion,String title,String slug,Map<String,?> values){
  validate(context,typeId,schemaVersion,values);
  String body=Objects.requireNonNull(payloadCodec.encode(Map.copyOf(values)),"Encoded content body");
  ContentEntry entry=ContentEntry.create(context.tenantId(),title,slug,body,typeId,schemaVersion,context.actorId(),clock.instant());
  repository.create(entry);return entry;
 }
 public ContentEntry revise(ContentCommandContext context,ContentId id,int expectedRevision,ContentTypeId typeId,int schemaVersion,
   String title,String slug,Map<String,?> values){
  validate(context,typeId,schemaVersion,values);
  String body=Objects.requireNonNull(payloadCodec.encode(Map.copyOf(values)),"Encoded content body");
  ContentEntry current=repository.find(context.tenantId(),id).orElseThrow(ContentNotFoundException::new);
  ContentEntry revised=current.revise(expectedRevision,title,slug,body,typeId,schemaVersion,context.actorId(),clock.instant());
  if(!repository.appendRevision(revised,expectedRevision))throw new RevisionConflictException(expectedRevision,
   repository.find(context.tenantId(),id).map(ContentEntry::currentRevisionNumber).orElse(expectedRevision+1));
  return revised;
 }
 private void validate(ContentCommandContext context,ContentTypeId typeId,int version,Map<String,?> values){
  ContentValidationResult result=types.validate(context,typeId,version,values);
  if(!result.valid())throw new ContentValidationException(result);
 }
 public ContentEntry get(ContentCommandContext context,ContentId id){return repository.find(context.tenantId(),id).orElseThrow(ContentNotFoundException::new);}
 public void archive(ContentCommandContext context,ContentId id){
  ContentEntry current=get(context,id);ContentEntry archived=current.archive(clock.instant());
  if(!repository.updateStatus(context.tenantId(),id,archived.status(),archived.updatedAt()))throw new IllegalStateException("Content status changed concurrently");
 }
 public void restore(ContentCommandContext context,ContentId id){
  ContentEntry current=get(context,id);ContentEntry restored=current.restore(clock.instant());
  if(!repository.updateStatus(context.tenantId(),id,restored.status(),restored.updatedAt()))throw new IllegalStateException("Content status changed concurrently");
 }
}
