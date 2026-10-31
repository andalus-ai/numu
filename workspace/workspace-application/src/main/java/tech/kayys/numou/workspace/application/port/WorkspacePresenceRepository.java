package tech.kayys.numou.workspace.application.port;
import java.time.Instant; import java.util.List; import java.util.UUID;
/** Tenant-scoped ephemeral presence storage. Presence is advisory and never grants authorization. */
public interface WorkspacePresenceRepository {
 boolean heartbeat(UUID tenantId,UUID pageId,UUID actorId,UUID sessionId,Instant lastSeenAt,Instant expiresAt);
 List<PresenceSession> findActive(UUID tenantId,UUID pageId,Instant now);
 boolean leave(UUID tenantId,UUID pageId,UUID actorId,UUID sessionId);
 int purgeExpired(Instant now,int limit);
 record PresenceSession(UUID actorId,Instant lastSeenAt) {}
}
