package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class PickupRequestTest {
 private final Instant now=Instant.parse("2026-10-24T02:00:00Z");
 private PickupRequest request(){return PickupRequest.request(PickupRequestId.generate(),TenantId.generate(),ShipmentId.generate(),
  "PICK-001","MERCHANT-001","Jl. Asia Afrika, Bandung",now.plusSeconds(3600),now.plusSeconds(7200),"merchant-user",now);}
 @Test void collectionRequiresAssignmentAndEvidence(){
  PickupRequest p=request();assertThrows(IllegalStateException.class,()->p.recordCollection("media://pickup/1",now.plusSeconds(10)));
  p.assign("courier-17",now.plusSeconds(20));assertThrows(IllegalArgumentException.class,()->p.recordCollection(" ",now.plusSeconds(30)));
  p.recordCollection("media://pickup/opaque-id",now.plusSeconds(40));assertEquals(PickupRequestStatus.COLLECTED,p.status());
  assertEquals("courier-17",p.courierReference());assertThrows(IllegalStateException.class,()->p.cancel(now.plusSeconds(50)));
 }
 @Test void failedPickupCanBeReassignedWithoutErasingLastFailure(){
  PickupRequest p=request();p.assign("courier-17",now.plusSeconds(20));p.recordFailedAttempt("Merchant unavailable",now.plusSeconds(30));
  assertEquals(PickupRequestStatus.FAILED,p.status());p.assign("courier-22",now.plusSeconds(40));
  assertEquals(PickupRequestStatus.ASSIGNED,p.status());assertEquals("courier-22",p.courierReference());
  assertEquals("Merchant unavailable",p.failureReason());
 }
 @Test void rejectsInvalidWindowAndBackwardsTransitions(){
  assertThrows(IllegalArgumentException.class,()->PickupRequest.request(PickupRequestId.generate(),TenantId.generate(),ShipmentId.generate(),
   "PICK-002","M-2","Address",now.plusSeconds(7200),now.plusSeconds(3600),"merchant",now));
  PickupRequest p=request();assertThrows(IllegalArgumentException.class,()->p.assign("courier",now.minusSeconds(1)));
 }
}