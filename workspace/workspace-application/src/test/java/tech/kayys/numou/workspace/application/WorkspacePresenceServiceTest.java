package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.application.port.WorkspacePresenceRepository;
import tech.kayys.numou.workspace.domain.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspacePresenceServiceTest {
 private static final Instant NOW=Instant.parse("2026-10-31T00:00:00Z");

 @Test void aggregatesMultipleTabsAndRequiresReadAccess() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),pageId=UUID.randomUUID();
  Page page=new Page(pageId,tenant,UUID.randomUUID(),UUID.randomUUID(),null,"Presence",List.of(),1,NOW,NOW);
  MemoryPresence memory=new MemoryPresence();
  memory.heartbeat(tenant,pageId,actor,UUID.randomUUID(),NOW,NOW.plusSeconds(60));
  memory.heartbeat(tenant,pageId,actor,UUID.randomUUID(),NOW.plusSeconds(1),NOW.plusSeconds(61));
  UUID other=UUID.randomUUID();
  memory.heartbeat(tenant,pageId,other,UUID.randomUUID(),NOW,NOW.plusSeconds(60));
  WorkspacePresenceService service=new WorkspacePresenceService(
   pageRepository(page),
   (t,a,r,id,action)->{assertEquals(tenant,t);assertEquals(pageId,id);assertEquals(Action.READ,action);},
   memory,Clock.fixed(NOW.plusSeconds(2),ZoneOffset.UTC));
  var snapshot=service.list(tenant,actor,pageId);
  assertEquals(2,snapshot.users().size());
  assertEquals(2,snapshot.users().stream().filter(u->u.actorId().equals(actor)).findFirst().orElseThrow().activeSessions());
  assertEquals(NOW.plusSeconds(1),snapshot.users().stream().filter(u->u.actorId().equals(actor)).findFirst().orElseThrow().lastSeenAt());
 }

 @Test void heartbeatUsesFixedLeaseAndSessionCannotBeTakenOver() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),pageId=UUID.randomUUID(),session=UUID.randomUUID();
  Page page=new Page(pageId,tenant,UUID.randomUUID(),UUID.randomUUID(),null,"Presence",List.of(),1,NOW,NOW);
  MemoryPresence memory=new MemoryPresence();
  WorkspacePresenceService service=new WorkspacePresenceService(pageRepository(page),
   (t,a,r,id,action)->{},memory,Clock.fixed(NOW,ZoneOffset.UTC));
  service.heartbeat(tenant,actor,pageId,session);
  assertEquals(NOW.plusSeconds(60),memory.rows.get(session).expiresAt());
  assertThrows(IllegalArgumentException.class,()->memory.heartbeat(tenant,pageId,UUID.randomUUID(),session,NOW.plusSeconds(1),NOW.plusSeconds(61)));
  assertFalse(memory.leave(tenant,pageId,UUID.randomUUID(),session));
  assertTrue(memory.leave(tenant,pageId,actor,session));
 }

 @Test void missingPageIsRejectedBeforePresenceRead() {
  MemoryPresence memory=new MemoryPresence();
  WorkspacePresenceService service=new WorkspacePresenceService(pageRepository(null),
   (t,a,r,id,action)->fail("authorization must not run for missing page"),memory,Clock.fixed(NOW,ZoneOffset.UTC));
  assertThrows(IllegalArgumentException.class,()->service.list(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()));
 }

 private static PageRepository pageRepository(Page page){return new PageRepository(){
  public Optional<Page> findPage(UUID tenant,UUID id){return page!=null&&page.tenantId().equals(tenant)&&page.id().equals(id)?Optional.of(page):Optional.empty();}
  public List<Page> findChildren(UUID tenant,UUID space,UUID parent){return List.of();}
  public boolean saveWithRevision(Page p,PageRevision r,long expected){return false;}
  public List<PageRevision> history(UUID tenant,UUID id){return List.of();}
 };}
 private static final class MemoryPresence implements WorkspacePresenceRepository {
  final Map<UUID,Row> rows=new HashMap<>();
  public boolean heartbeat(UUID tenant,UUID page,UUID actor,UUID session,Instant seen,Instant expires){
   Row old=rows.get(session);if(old!=null&&!old.actor().equals(actor))return false;
   rows.put(session,new Row(tenant,page,actor,seen,expires));return true;
  }
  public List<PresenceSession> findActive(UUID tenant,UUID page,Instant now){return rows.values().stream().filter(r->r.tenant().equals(tenant)&&r.page().equals(page)&&r.expiresAt().isAfter(now)).map(r->new PresenceSession(r.actor(),r.lastSeen())).toList();}
  public boolean leave(UUID tenant,UUID page,UUID actor,UUID session){Row r=rows.get(session);if(r==null||!r.tenant().equals(tenant)||!r.page().equals(page)||!r.actor().equals(actor))return false;rows.remove(session);return true;}
  public int purgeExpired(Instant now,int limit){List<UUID> ids=rows.entrySet().stream().filter(e->!e.getValue().expiresAt().isAfter(now)).limit(limit).map(Map.Entry::getKey).toList();ids.forEach(rows::remove);return ids.size();}
  record Row(UUID tenant,UUID page,UUID actor,Instant lastSeen,Instant expiresAt){}
 }
}
