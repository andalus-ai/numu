package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CourierEtaEstimateTest {
 @Test void retainsSourceAndRejectsInvalidConfidenceOrWindow(){
  Instant now=Instant.parse("2026-10-24T00:00:00Z");
  CourierEtaEstimate eta=new CourierEtaEstimate(UUID.randomUUID(),TenantId.generate(),ShipmentId.generate(),
   now.plusSeconds(600),now.plusSeconds(3600),now,new BigDecimal("0.85"),"rules-2026.10",
   CourierEtaEstimate.Source.RULES,"STANDARD_LANE_BASELINE");
  assertEquals("rules-2026.10",eta.modelVersion());
  assertThrows(IllegalArgumentException.class,()->new CourierEtaEstimate(UUID.randomUUID(),TenantId.generate(),ShipmentId.generate(),
   now.plusSeconds(600),now.plusSeconds(3600),now,new BigDecimal("1.01"),"v1",CourierEtaEstimate.Source.RULES,"TEST"));
  assertThrows(IllegalArgumentException.class,()->new CourierEtaEstimate(UUID.randomUUID(),TenantId.generate(),ShipmentId.generate(),
   now.plusSeconds(3600),now.plusSeconds(600),now,new BigDecimal("0.5"),"v1",CourierEtaEstimate.Source.RULES,"TEST"));
 }
}