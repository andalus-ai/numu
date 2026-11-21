package tech.kayys.numou.history.graph.application;
import java.time.*; import java.util.Objects; import java.util.concurrent.atomic.AtomicBoolean;
/** Scheduler-neutral worker entry point; scheduling and shutdown belong to the host runtime. */
public final class HistoricalRelationshipProjectionWorker {
 private final HistoricalRelationshipProjectionDispatcher dispatcher; private final Clock clock; private final AtomicBoolean running=new AtomicBoolean(); private volatile RunSnapshot lastRun;
 public HistoricalRelationshipProjectionWorker(HistoricalRelationshipProjectionDispatcher dispatcher,Clock clock) { this.dispatcher=Objects.requireNonNull(dispatcher); this.clock=Objects.requireNonNull(clock); }
 public RunSnapshot runOnce(int limit) {
  if(!running.compareAndSet(false,true)) return new RunSnapshot(false,null,clock.instant(),null);
  Instant start=clock.instant(); try { var result=dispatcher.dispatch(limit); var snapshot=new RunSnapshot(true,start,clock.instant(),result); lastRun=snapshot; return snapshot; } finally { running.set(false); }
 }
 public boolean isRunning(){return running.get();} public RunSnapshot lastRun(){return lastRun;}
 public record RunSnapshot(boolean executed,Instant startedAt,Instant finishedAt,HistoricalRelationshipProjectionDispatcher.DispatchResult dispatch){}
}