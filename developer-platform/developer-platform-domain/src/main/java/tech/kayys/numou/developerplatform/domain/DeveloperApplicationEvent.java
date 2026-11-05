package tech.kayys.numou.developerplatform.domain;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** Immutable, secret-free audit/outbox event metadata. */
public record DeveloperApplicationEvent(UUID eventId,TenantId tenantId,DeveloperApplicationId applicationId,
 String eventType,long revision,String actorId,String correlationId,Instant occurredAt) {
 public DeveloperApplicationEvent {
  Objects.requireNonNull(eventId); Objects.requireNonNull(tenantId); Objects.requireNonNull(applicationId); Objects.requireNonNull(occurredAt);
  if(eventType==null || !eventType.matches("^[A-Z][A-Z0-9_]{1,99}$")) throw new IllegalArgumentException("invalid event type");
  if(revision<1) throw new IllegalArgumentException("revision must be positive");
  if(actorId==null || actorId.isBlank() || actorId.trim().length()>200) throw new IllegalArgumentException("actorId is required");
  actorId=actorId.trim(); correlationId=correlationId==null?"":correlationId.trim();
  if(correlationId.length()>200) throw new IllegalArgumentException("correlationId is too long");
 }
}