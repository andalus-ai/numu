package tech.kayys.numou.content.application;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.spi.ContentRepository;
import java.time.Clock;
import java.util.Objects;
public final class ContentApplicationService {
 private final ContentRepository repository; private final Clock clock;
 public ContentApplicationService(ContentRepository repository,Clock clock){this.repository=Objects.requireNonNull(repository);this.clock=Objects.requireNonNull(clock);}
 public ContentEntry create(ContentCommandContext context,String title,String slug,String body){
  var entry=ContentEntry.create(context.tenantId(),title,slug,body,context.actorId(),clock.instant());repository.create(entry);return entry;
 }
 public ContentEntry revise(ContentCommandContext context,ContentId id,int expectedRevision,String title,String slug,String body){
  var current=repository.find(context.tenantId(),id).orElseThrow(ContentNotFoundException::new);
  var revised=current.revise(expectedRevision,title,slug,body,context.actorId(),clock.instant());
  if(!repository.appendRevision(revised,expectedRevision)) throw new RevisionConflictException(expectedRevision,repository.find(context.tenantId(),id).map(ContentEntry::currentRevisionNumber).orElse(expectedRevision+1));
  return revised;
 }
 public ContentEntry get(ContentCommandContext context,ContentId id){return repository.find(context.tenantId(),id).orElseThrow(ContentNotFoundException::new);}
 public void archive(ContentCommandContext context,ContentId id){
  var current=get(context,id);var archived=current.archive(clock.instant());
  if(!repository.updateStatus(context.tenantId(),id,archived.status(),archived.updatedAt())) throw new IllegalStateException("Content status changed concurrently");
 }
 public void restore(ContentCommandContext context,ContentId id){
  var current=get(context,id);var restored=current.restore(clock.instant());
  if(!repository.updateStatus(context.tenantId(),id,restored.status(),restored.updatedAt())) throw new IllegalStateException("Content status changed concurrently");
 }
}