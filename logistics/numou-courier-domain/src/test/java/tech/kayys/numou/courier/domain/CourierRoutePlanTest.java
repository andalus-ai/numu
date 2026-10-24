package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CourierRoutePlanTest {
 private final Instant now=Instant.parse("2026-10-24T00:00:00Z");
 @Test void acceptsOrderedStopsAndRejectsDuplicateReferences(){
  CourierRoutePlan plan=new CourierRoutePlan(UUID.randomUUID(),TenantId.generate(),"BDG-01","courier-7",LocalDate.parse("2026-10-24"),
   1,CourierRoutePlan.Status.OPTIMIZED,now,List.of(
    new CourierRoutePlan.Stop(1,"shipment-1",CourierRoutePlan.Stop.StopType.PICKUP,now.plusSeconds(60)),
    new CourierRoutePlan.Stop(2,"shipment-2",CourierRoutePlan.Stop.StopType.DELIVERY,now.plusSeconds(120))),12000L,1800);
  assertEquals(2,plan.stops().size());
  assertThrows(IllegalArgumentException.class,()->new CourierRoutePlan(UUID.randomUUID(),TenantId.generate(),"BDG-01","courier-7",
   LocalDate.parse("2026-10-24"),1,CourierRoutePlan.Status.DRAFT,now,List.of(
    new CourierRoutePlan.Stop(1,"same",CourierRoutePlan.Stop.StopType.PICKUP,now.plusSeconds(60)),
    new CourierRoutePlan.Stop(2,"same",CourierRoutePlan.Stop.StopType.DELIVERY,now.plusSeconds(120))),null,null));
 }
}