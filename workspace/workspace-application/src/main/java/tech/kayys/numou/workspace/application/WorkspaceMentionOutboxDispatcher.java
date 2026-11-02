package tech.kayys.numou.workspace.application;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDeliveryPort;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore.ClaimedMention;
import java.time.Clock; import java.time.Duration; import java.time.Instant; import java.util.List; import java.util.Objects;
/** Lease-based dispatcher. Delivery is at-least-once; host adapter must honor idempotencyKey. */
public final class WorkspaceMentionOutboxDispatcher {
 private final WorkspaceMentionOutboxStore store; private final WorkspaceMentionDeliveryPort delivery; private final Clock clock; private final String workerId; private final int maxAttempts; private final Duration leaseDuration;
 public WorkspaceMentionOutboxDispatcher(WorkspaceMentionOutboxStore store,WorkspaceMentionDeliveryPort delivery,Clock clock,String workerId,int maxAttempts,Duration leaseDuration){
  this.store=Objects.requireNonNull(store);this.delivery=Objects.requireNonNull(delivery);this.clock=Objects.requireNonNull(clock);
  if(workerId==null||workerId.isBlank())throw new IllegalArgumentException("workerId required");if(maxAttempts<1||maxAttempts>100)throw new IllegalArgumentException("maxAttempts must be 1..100");if(leaseDuration==null||leaseDuration.isZero()||leaseDuration.isNegative())throw new IllegalArgumentException("leaseDuration must be positive");
  this.workerId=workerId;this.maxAttempts=maxAttempts;this.leaseDuration=leaseDuration;
 }
 public DispatchResult dispatch(int limit){
  if(limit<1||limit>500)throw new IllegalArgumentException("limit must be 1..500");Instant now=clock.instant();List<ClaimedMention> batch=store.claimDue(workerId,now,now.plus(leaseDuration),limit);int delivered=0,retried=0,dead=0;
  for(ClaimedMention item:batch)try{delivery.deliver(item);if(store.markDelivered(item.tenantId(),item.id(),item.leaseToken(),clock.instant()))delivered++;}
  catch(Exception failure){String error=safeError(failure);if(item.retryAttempts()>=maxAttempts){if(store.markDead(item.tenantId(),item.id(),item.leaseToken(),error,clock.instant()))dead++;}else if(store.scheduleRetry(item.tenantId(),item.id(),item.leaseToken(),clock.instant().plus(backoff(item.retryAttempts())),error,clock.instant()))retried++;}
  return new DispatchResult(batch.size(),delivered,retried,dead);
 }
 private static Duration backoff(int attempt){return Duration.ofSeconds(Math.min(3600,5L<<Math.min(10,Math.max(0,attempt-1))));}
 private static String safeError(Exception e){String m=e.getMessage();if(m==null||m.isBlank())m=e.getClass().getSimpleName();return m.length()>2000?m.substring(0,2000):m;}
 public record DispatchResult(int claimed,int delivered,int retried,int deadLettered){}
}
