package tech.kayys.numou.history.graph.application;
import org.junit.jupiter.api.Test;import java.time.Duration;import static org.junit.jupiter.api.Assertions.*;
class HistoricalProjectionRetryPolicyTest {
 @Test void exponentialDelayIsCappedAndAttemptsBounded(){var p=new HistoricalProjectionRetryPolicy(4,Duration.ofSeconds(2),Duration.ofSeconds(10));assertEquals(Duration.ofSeconds(2),p.delayAfter(1));assertEquals(Duration.ofSeconds(4),p.delayAfter(2));assertEquals(Duration.ofSeconds(8),p.delayAfter(3));assertEquals(Duration.ofSeconds(10),p.delayAfter(4));assertTrue(p.exhausted(4));assertFalse(p.exhausted(3));}
 @Test void rejectsInvalidPolicy(){assertThrows(IllegalArgumentException.class,()->new HistoricalProjectionRetryPolicy(0,Duration.ofSeconds(1),Duration.ofSeconds(1)));assertThrows(IllegalArgumentException.class,()->new HistoricalProjectionRetryPolicy(2,Duration.ofSeconds(2),Duration.ofSeconds(1)));}
}