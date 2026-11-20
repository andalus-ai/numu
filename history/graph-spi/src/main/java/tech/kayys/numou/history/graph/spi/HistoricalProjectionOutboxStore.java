package tech.kayys.numou.history.graph.spi;
import java.time.Instant; import java.util.List; import java.util.UUID;
/** Durable at-least-once delivery contract for graph projection events. */
public interface HistoricalProjectionOutboxStore {
 List<ClaimedProjectionEvent> claimDue(String workerId,Instant now,Instant leaseUntil,int limit);
 boolean markPublished(UUID eventId,UUID leaseToken,Instant now);
 boolean scheduleRetry(UUID eventId,UUID leaseToken,Instant availableAt,String error,Instant now);
 boolean markDead(UUID eventId,UUID leaseToken,String error,Instant now);
 List<DeadProjectionEvent> listDead(int limit);
 boolean requeueDead(UUID eventId,UUID actorId,Instant now);
 record ClaimedProjectionEvent(UUID eventId,String eventKey,String eventType,int schemaVersion,UUID tenantId,UUID aggregateId,long aggregateRevision,String payload,Instant occurredAt,int attempts,int retryAttempts,UUID leaseToken,Instant leaseUntil) {}
 record DeadProjectionEvent(UUID eventId,String eventKey,String eventType,int schemaVersion,UUID tenantId,UUID aggregateId,long aggregateRevision,String payload,int attempts,int retryAttempts,String lastError,Instant deadLetteredAt) {}
}