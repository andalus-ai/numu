package tech.kayys.numou.history.graph.application;
import java.time.Duration; import java.util.Objects;
/** Bounded exponential retry policy with a capped delay. */
public record HistoricalProjectionRetryPolicy(int maxAttempts,Duration initialDelay,Duration maxDelay) {
 public HistoricalProjectionRetryPolicy {
  if(maxAttempts<1) throw new IllegalArgumentException("maxAttempts must be positive");
  Objects.requireNonNull(initialDelay); Objects.requireNonNull(maxDelay);
  if(initialDelay.isNegative()||initialDelay.isZero()||maxDelay.compareTo(initialDelay)<0) throw new IllegalArgumentException("invalid retry delays");
 }
 public Duration delayAfter(int attempt) {
  if(attempt<1) throw new IllegalArgumentException("attempt must be positive");
  try { Duration d=initialDelay.multipliedBy(1L<<Math.min(attempt-1,30)); return d.compareTo(maxDelay)>0?maxDelay:d; }
  catch(ArithmeticException e) { return maxDelay; }
 }
 public boolean exhausted(int attempts) { return attempts>=maxAttempts; }
}