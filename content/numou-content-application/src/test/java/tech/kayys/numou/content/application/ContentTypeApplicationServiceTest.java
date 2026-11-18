package tech.kayys.numou.content.application;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.ActorId;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.model.*;
import tech.kayys.numou.content.spi.ContentTypeRepository;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ContentTypeApplicationServiceTest {
 static class MemoryRepository implements ContentTypeRepository {
  final Map<String,ContentTypeSchema> schemas=new HashMap<>();
  private String key(TenantId t,ContentTypeId id,int v){return t+":"+id+":"+v;}
  public void createType(ContentTypeSchema s){schemas.put(key(s.tenantId(),s.typeId(),s.version()),s);}
  public void createVersion(ContentTypeSchema s){schemas.put(key(s.tenantId(),s.typeId(),s.version()),s);}
  public Optional<ContentTypeSchema> findVersion(TenantId t,ContentTypeId id,int v){return Optional.ofNullable(schemas.get(key(t,id,v)));}
  public boolean publish(TenantId t,ContentTypeId id,int v,Instant at){String k=key(t,id,v);ContentTypeSchema s=schemas.get(k);if(s==null||s.status()!=SchemaStatus.DRAFT)return false;schemas.put(k,s.publish(at));return true;}
 }
 @Test void createsPublishesAndValidatesTenantScopedVersion(){
  var repo=new MemoryRepository();var service=new ContentTypeApplicationService(repo,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC));
  var actor=new ActorId(UUID.randomUUID());var ctx=new ContentCommandContext(new TenantId(UUID.randomUUID()),actor);
  var s=service.createType(ctx,"article","Article",List.of(new ContentFieldDefinition("title",FieldType.STRING,true,false,3,120,null,null,null,List.of())));
  service.publish(ctx,s.typeId(),1);
  assertTrue(service.validate(ctx,s.typeId(),1,Map.of("title","Hello world")).valid());
  assertFalse(service.validate(ctx,s.typeId(),1,Map.of("title","x")).valid());
  assertThrows(ContentNotFoundException.class,()->service.get(new ContentCommandContext(new TenantId(UUID.randomUUID()),actor),s.typeId(),1));
 }
 @Test void changesCreateNewVersionWithoutMutatingPublishedSnapshot(){
  var repo=new MemoryRepository();var service=new ContentTypeApplicationService(repo,Clock.systemUTC());
  var ctx=new ContentCommandContext(new TenantId(UUID.randomUUID()),new ActorId(UUID.randomUUID()));
  var v1=service.createType(ctx,"article","Article",List.of(new ContentFieldDefinition("title",FieldType.STRING,true,false,null,100,null,null,null,List.of())));
  service.publish(ctx,v1.typeId(),1);
  var v2=service.createVersion(ctx,v1.typeId(),1,List.of(
   new ContentFieldDefinition("title",FieldType.TEXT,true,false,null,1000,null,null,null,List.of()),
   new ContentFieldDefinition("summary",FieldType.TEXT,false,false,null,500,null,null,null,List.of())));
  assertEquals(SchemaStatus.PUBLISHED,service.get(ctx,v1.typeId(),1).status());assertEquals(2,v2.version());
  assertThrows(IllegalStateException.class,()->service.validate(ctx,v1.typeId(),2,Map.of("title","hello")));
  assertTrue(service.validateDraft(ctx,v1.typeId(),2,Map.of("title","hello")).valid());
 }
}
