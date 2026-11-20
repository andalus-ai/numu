package tech.kayys.numou.history.graph.application;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import java.time.*; import java.util.*;
/** Claims and processes a bounded batch using lease-token-fenced acknowledgements. */
public final class HistoricalRelationshipProjectionDispatcher {
 private final HistoricalProjectionOutboxStore store; private final HistoricalRelationshipProjectionPort projection;
 private final HistoricalProjectionRetryPolicy retry; private final Clock clock; private final String workerId; private final Duration lease;
 public HistoricalRelationshipProjectionDispatcher(HistoricalProjectionOutboxStore store,HistoricalRelationshipProjectionPort projection,HistoricalProjectionRetryPolicy retry,Clock clock,String workerId,Duration lease) {
  this.store=Objects.requireNonNull(store); this.projection=Objects.requireNonNull(projection); this.retry=Objects.requireNonNull(retry); this.clock=Objects.requireNonNull(clock);
  if(workerId==null||workerId.isBlank()||workerId.length()>160) throw new IllegalArgumentException("workerId must be 1..160 characters");
  this.workerId=workerId; this.lease=Objects.requireNonNull(lease); if(lease.isNegative()||lease.isZero()) throw new IllegalArgumentException("lease must be positive");
 }
 public DispatchResult dispatch(int limit) {
  if(limit<1||limit>500) throw new IllegalArgumentException("limit must be 1..500");
  Instant now=clock.instant(); List<ClaimedProjectionEvent> batch=store.claimDue(workerId,now,now.plus(lease),limit);
  int ok=0,retryCount=0,dead=0,lost=0;
  for(var e:batch) try { projection.project(e); if(store.markPublished(e.eventId(),e.leaseToken(),clock.instant())) ok++; else lost++; }
  catch(Exception failure) {
   Instant at=clock.instant(); String error=safeError(failure);
   if(retry.exhausted(e.retryAttempts())) { if(store.markDead(e.eventId(),e.leaseToken(),error,at)) dead++; else lost++; }
   else if(store.scheduleRetry(e.eventId(),e.leaseToken(),at.plus(retry.delayAfter(e.retryAttempts())),error,at)) retryCount++; else lost++;
  }
  return new DispatchResult(batch.size(),ok,retryCount,dead,lost);
 }
 private static String safeError(Exception e) { String s=e.getClass().getSimpleName()+": "+Objects.toString(e.getMessage(),"projection failed"); s=s.replaceAll("[\\r\\n\\t]+"," "); return s.length()>2000?s.substring(0,2000):s; }
 public record DispatchResult(int claimed,int published,int scheduledForRetry,int deadLettered,int leaseLost) {}
}