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
  var current=get(context,id);var revised=current.revise(expectedRevision,title,slug,body,context.actorId(),clock.instant());
  if(!repository.appendRevision(revised,expectedRevision)) throw new RevisionConflictException(expectedRevision,repository.find(context.tenantId(),id).map(ContentEntry::currentRevisionNumber).orElse(expectedRevision+1));
  return revised;
 }
 public ContentEntry get(ContentCommandContext context,ContentId id){return repository.find(context.tenantId(),id).orElseThrow(ContentNotFoundException::new);}
 public ContentEntry transition(ContentCommandContext context,ContentId id,ContentStatus target){
  var current=get(context,id);var transitioned=current.transitionTo(target,clock.instant());
  if(!repository.transitionStatus(context.tenantId(),id,current.status(),target,transitioned.updatedAt()))
   throw new IllegalStateException("Content lifecycle state changed concurrently; reload before retrying");
  return transitioned;
 }
 public ContentEntry archive(ContentCommandContext context,ContentId id){return transition(context,id,ContentStatus.ARCHIVED);}
 public ContentEntry restore(ContentCommandContext context,ContentId id){return transition(context,id,ContentStatus.DRAFT);}
}
