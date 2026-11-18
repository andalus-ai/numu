package tech.kayys.numou.content.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.model.*;
import tech.kayys.numou.content.spi.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContentApplicationServiceTest {
    static class EntryRepository implements ContentRepository {
        final Map<String,ContentEntry> entries=new HashMap<>();
        private String key(TenantId t,ContentId id){return t+":"+id;}
        public void create(ContentEntry e){entries.put(key(e.tenantId(),e.id()),e);}
        public Optional<ContentEntry> find(TenantId t,ContentId id){return Optional.ofNullable(entries.get(key(t,id)));}
        public boolean appendRevision(ContentEntry e,int expected){
            String k=key(e.tenantId(),e.id());ContentEntry old=entries.get(k);
            if(old==null||old.currentRevisionNumber()!=expected||old.status()!=ContentStatus.DRAFT)return false;
            entries.put(k,e);return true;
        }
        public boolean transitionStatus(TenantId t,ContentId id,ContentStatus expected,ContentStatus target,Instant at){
            String k=key(t,id);ContentEntry e=entries.get(k);
            if(e==null||e.status()!=expected)return false;
            entries.put(k,ContentEntry.rehydrate(e.id(),t,target,e.revisions(),e.createdAt(),at,e.currentRevisionNumber()));
            return true;
        }
    }
    static class TypeRepository implements ContentTypeRepository {
        final Map<String,ContentTypeSchema> schemas=new HashMap<>();
        private String key(TenantId t,ContentTypeId id,int v){return t+":"+id+":"+v;}
        public void createType(ContentTypeSchema s){schemas.put(key(s.tenantId(),s.typeId(),s.version()),s);}
        public void createVersion(ContentTypeSchema s){schemas.put(key(s.tenantId(),s.typeId(),s.version()),s);}
        public Optional<ContentTypeSchema> findVersion(TenantId t,ContentTypeId id,int v){return Optional.ofNullable(schemas.get(key(t,id,v)));}
        public boolean publish(TenantId t,ContentTypeId id,int v,Instant at){
            String k=key(t,id,v);ContentTypeSchema s=schemas.get(k);
            if(s==null||s.status()!=SchemaStatus.DRAFT)return false;
            schemas.put(k,s.publish(at));return true;
        }
    }
    private ContentTypeApplicationService types(TypeRepository repo){
        return new ContentTypeApplicationService(repo,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC));
    }
    private ContentApplicationService service(EntryRepository entries,ContentTypeApplicationService types){
        return new ContentApplicationService(entries,types,values->new TreeMap<>(values).toString(),
                Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"),ZoneOffset.UTC));
    }
    private List<ContentFieldDefinition> fields(){
        return List.of(new ContentFieldDefinition("headline",FieldType.STRING,true,false,3,120,null,null,null,List.of()));
    }

    @Test void createsOnlyAfterPublishedSchemaValidationAndKeepsTenantIsolation(){
        var entries=new EntryRepository();var types=types(new TypeRepository());var service=service(entries,types);
        var actor=new ActorId(UUID.randomUUID());var tenant=new TenantId(UUID.randomUUID());
        var context=new ContentCommandContext(tenant,actor);
        var type=types.createType(context,"article","Article",fields());types.publish(context,type.typeId(),1);
        var created=service.create(context,type.typeId(),1,"Title","title",Map.of("headline","Numou CMS"));
        assertEquals(type.typeId(),created.currentRevision().contentTypeId());
        assertEquals(1,created.currentRevision().schemaVersion());
        assertEquals(created.id(),service.get(context,created.id()).id());
        assertThrows(ContentNotFoundException.class,()->service.get(
                new ContentCommandContext(new TenantId(UUID.randomUUID()),actor),created.id()));
    }

    @Test void invalidContentNeverReachesPersistence(){
        var entries=new EntryRepository();var types=types(new TypeRepository());var service=service(entries,types);
        var context=new ContentCommandContext(new TenantId(UUID.randomUUID()),new ActorId(UUID.randomUUID()));
        var type=types.createType(context,"article","Article",fields());types.publish(context,type.typeId(),1);
        var error=assertThrows(ContentValidationException.class,()->service.create(
                context,type.typeId(),1,"Title","title",Map.of("headline","x")));
        assertFalse(error.result().valid());assertTrue(entries.entries.isEmpty());
    }

    @Test void eachRevisionRetainsTheExactSchemaVersionUsedForValidation(){
        var entries=new EntryRepository();var types=types(new TypeRepository());var service=service(entries,types);
        var context=new ContentCommandContext(new TenantId(UUID.randomUUID()),new ActorId(UUID.randomUUID()));
        var v1=types.createType(context,"article","Article",fields());types.publish(context,v1.typeId(),1);
        var created=service.create(context,v1.typeId(),1,"First","first",Map.of("headline","First headline"));
        types.createVersion(context,v1.typeId(),1,List.of(
                new ContentFieldDefinition("headline",FieldType.STRING,true,false,3,120,null,null,null,List.of()),
                new ContentFieldDefinition("summary",FieldType.TEXT,false,false,null,500,null,null,null,List.of())));
        types.publish(context,v1.typeId(),2);
        var revised=service.revise(context,created.id(),1,v1.typeId(),2,"Second","second",
                Map.of("headline","Second headline","summary","Updated summary"));
        assertEquals(1,revised.revisions().get(0).schemaVersion());
        assertEquals(2,revised.revisions().get(1).schemaVersion());
        assertEquals(2,revised.currentRevisionNumber());
    }

    @Test void editorialLifecycleStillUsesCompareAndSetAndPreservesRevision(){
        var entries=new EntryRepository();var types=types(new TypeRepository());var service=service(entries,types);
        var context=new ContentCommandContext(new TenantId(UUID.randomUUID()),new ActorId(UUID.randomUUID()));
        var type=types.createType(context,"article","Article",fields());types.publish(context,type.typeId(),1);
        var created=service.create(context,type.typeId(),1,"Title","title",Map.of("headline","Headline"));
        var inReview=service.transition(context,created.id(),ContentStatus.IN_REVIEW);
        assertEquals(ContentStatus.IN_REVIEW,inReview.status());
        assertEquals(1,inReview.currentRevisionNumber());
        assertEquals(ContentStatus.IN_REVIEW,service.get(context,created.id()).status());
    }
}
