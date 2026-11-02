package tech.kayys.numou.workspace.application;
import tech.kayys.numou.workspace.application.WorkspacePorts.AccessPolicy;
import tech.kayys.numou.workspace.application.WorkspacePorts.Action;
import tech.kayys.numou.workspace.application.WorkspacePorts.PageRepository;
import tech.kayys.numou.workspace.application.WorkspacePorts.Resource;
import tech.kayys.numou.workspace.application.port.WorkspacePresenceRepository;
import java.time.Clock; import java.time.Duration; import java.time.Instant; import java.util.LinkedHashMap; import java.util.List; import java.util.Map; import java.util.Objects; import java.util.UUID;
/** Short-lived collaboration presence. It is separate from page locks and edit authorization. */
public final class WorkspacePresenceService {
 public static final Duration LEASE_DURATION=Duration.ofSeconds(60); private static final int MAX_ACTIVE_SESSIONS=500;
 private final PageRepository pages; private final AccessPolicy access; private final WorkspacePresenceRepository presence; private final Clock clock;
 public WorkspacePresenceService(PageRepository pages,AccessPolicy access,WorkspacePresenceRepository presence,Clock clock){this.pages=Objects.requireNonNull(pages);this.access=Objects.requireNonNull(access);this.presence=Objects.requireNonNull(presence);this.clock=Objects.requireNonNull(clock);}
 public PresenceSnapshot heartbeat(UUID tenant,UUID actor,UUID page,UUID session){authorize(tenant,actor,page);Objects.requireNonNull(session,"sessionId");Instant now=clock.instant();if(!presence.heartbeat(tenant,page,actor,session,now,now.plus(LEASE_DURATION)))throw new IllegalArgumentException("Presence session is already owned by another actor");return snapshot(page,tenant,actor);}
 public PresenceSnapshot list(UUID tenant,UUID actor,UUID page){authorize(tenant,actor,page);return snapshot(page,tenant,actor);}
 public void leave(UUID tenant,UUID actor,UUID page,UUID session){authorize(tenant,actor,page);Objects.requireNonNull(session,"sessionId");presence.leave(tenant,page,actor,session);}
 private PresenceSnapshot snapshot(UUID page,UUID tenant,UUID actor){Instant now=clock.instant();List<WorkspacePresenceRepository.PresenceSession> sessions=presence.findActive(tenant,page,now,MAX_ACTIVE_SESSIONS+1);boolean truncated=sessions.size()>MAX_ACTIVE_SESSIONS;Map<UUID,UserPresence> users=new LinkedHashMap<>();for(var s:sessions.stream().limit(MAX_ACTIVE_SESSIONS).toList()){UserPresence current=users.get(s.actorId());if(current==null)users.put(s.actorId(),new UserPresence(s.actorId(),1,s.lastSeenAt()));else users.put(s.actorId(),new UserPresence(s.actorId(),current.activeSessions()+1,current.lastSeenAt().isAfter(s.lastSeenAt())?current.lastSeenAt():s.lastSeenAt()));}return new PresenceSnapshot(page,now,users.values().stream().toList(),truncated);}
 private void authorize(UUID tenant,UUID actor,UUID page){Objects.requireNonNull(tenant,"tenantId");Objects.requireNonNull(actor,"actorId");Objects.requireNonNull(page,"pageId");pages.findPage(tenant,page).orElseThrow(()->new IllegalArgumentException("Page not found for tenant"));access.require(tenant,actor,Resource.PAGE,page,Action.READ);}
 public record UserPresence(UUID actorId,int activeSessions,Instant lastSeenAt){}
 public record PresenceSnapshot(UUID pageId,Instant observedAt,List<UserPresence> users,boolean truncated){public PresenceSnapshot{users=List.copyOf(users);}}
}
