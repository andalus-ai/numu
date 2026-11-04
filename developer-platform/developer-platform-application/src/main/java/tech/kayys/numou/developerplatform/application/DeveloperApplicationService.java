package tech.kayys.numou.developerplatform.application;
import tech.kayys.numou.developerplatform.domain.*;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.security.NoSuchAlgorithmException;
import java.time.Clock;import java.util.HexFormat;import java.util.List;import java.util.Objects;import java.util.Optional;import java.util.UUID;import java.util.concurrent.CompletableFuture;import java.util.concurrent.CompletionStage;
/** Use cases always derive tenant and actor from a verified host security context. */
public final class DeveloperApplicationService{
 private final DeveloperApplicationActorProvider actors;private final DeveloperApplicationAuthorizationPort authorization;private final DeveloperApplicationStore store;private final Clock clock;
 public DeveloperApplicationService(DeveloperApplicationActorProvider actors,DeveloperApplicationAuthorizationPort authorization,DeveloperApplicationStore store,Clock clock){this.actors=Objects.requireNonNull(actors);this.authorization=Objects.requireNonNull(authorization);this.store=Objects.requireNonNull(store);this.clock=Objects.requireNonNull(clock);}
 public CompletionStage<DeveloperApplicationStore.CreateResult> register(RegisterDeveloperApplicationCommand command){
  Objects.requireNonNull(command);var actor=Objects.requireNonNull(actors.currentActor(),"verified actor context");
  return authorization.requireCanRegister(actor).thenCompose(x->{var now=clock.instant();var app=DeveloperApplication.register(actor.tenantId(),command.slug(),command.name(),command.description(),actor.actorId(),now);
   var event=new DeveloperApplicationEvent(UUID.randomUUID(),actor.tenantId(),app.id(),"DEVELOPER_APPLICATION_REGISTERED",1,actor.actorId(),actor.correlationId(),now);
   String fingerprint=sha256(app.slug()+"\u0000"+app.name()+"\u0000"+app.description());
   return store.registerIfAbsent(app,sha256(command.idempotencyKey()),fingerprint,event);
  });
 }
 public CompletionStage<Optional<DeveloperApplication>> get(DeveloperApplicationId id){Objects.requireNonNull(id);var actor=Objects.requireNonNull(actors.currentActor(),"verified actor context");return authorization.requireCanRead(actor,id).thenCompose(x->store.find(actor.tenantId(),id));}
 public CompletionStage<List<DeveloperApplication>> list(UUID afterId,int limit){if(limit<1||limit>100)throw new IllegalArgumentException("limit must be 1-100");var actor=Objects.requireNonNull(actors.currentActor(),"verified actor context");return authorization.requireCanList(actor).thenCompose(x->store.list(actor.tenantId(),afterId,limit));}
 public CompletionStage<Boolean> updateProfile(UpdateDeveloperApplicationProfileCommand command){
  Objects.requireNonNull(command);var actor=Objects.requireNonNull(actors.currentActor(),"verified actor context");
  return authorization.requireCanManage(actor,command.applicationId()).thenCompose(x->store.find(actor.tenantId(),command.applicationId()).thenCompose(found->{
   if(found.isEmpty()||found.get().revision()!=command.expectedRevision())return CompletableFuture.completedFuture(false);
   var app=found.get();var event=app.updateProfile(command.name(),command.description(),actor.actorId(),actor.correlationId(),clock.instant());
   return store.update(app,command.expectedRevision(),event);
  }));
 }
 public CompletionStage<Boolean> changeStatus(ChangeDeveloperApplicationStatusCommand command){
  Objects.requireNonNull(command);var actor=Objects.requireNonNull(actors.currentActor(),"verified actor context");
  return authorization.requireCanManage(actor,command.applicationId()).thenCompose(x->store.find(actor.tenantId(),command.applicationId()).thenCompose(found->{
   if(found.isEmpty()||found.get().revision()!=command.expectedRevision())return CompletableFuture.completedFuture(false);
   var app=found.get();var now=clock.instant();DeveloperApplicationEvent event;
   switch(command.targetStatus()){
    case ACTIVE -> event=app.reactivate(actor.actorId(),actor.correlationId(),now);
    case SUSPENDED -> event=app.suspend(actor.actorId(),actor.correlationId(),now);
    case ARCHIVED -> event=app.archive(actor.actorId(),actor.correlationId(),now);
    default -> throw new IllegalStateException("unsupported application status");
   }
   return store.update(app,command.expectedRevision(),event);
  }));
 }
 private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}}
}