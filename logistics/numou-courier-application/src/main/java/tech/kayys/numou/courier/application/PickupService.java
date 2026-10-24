package tech.kayys.numou.courier.application;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
/** Application use cases for requesting, assigning and completing first-mile pickups. */
public final class PickupService {
 private final ShipmentRepository shipments; private final PickupRequestRepository pickups;
 public PickupService(ShipmentRepository shipments,PickupRequestRepository pickups){this.shipments=Objects.requireNonNull(shipments);this.pickups=Objects.requireNonNull(pickups);}
 public CompletionStage<PickupRequest> request(TenantId tenantId,PickupRequestId id,ShipmentId shipmentId,String reference,
  String merchantReference,String address,Instant windowStart,Instant windowEnd,String requestedBy,Instant now) {
  return shipments.findById(tenantId,shipmentId).thenCompose(found->{
   Shipment shipment=found.orElseThrow(()->new IllegalArgumentException("shipment not found for tenant"));
   if(shipment.status()!=ShipmentStatus.BOOKED)return CompletableFuture.failedFuture(new IllegalStateException("pickup requires a booked shipment"));
   return pickups.save(tenantId,PickupRequest.request(id,tenantId,shipmentId,reference,merchantReference,address,windowStart,windowEnd,requestedBy,now));
  });
 }
 public CompletionStage<PickupRequest> assign(TenantId tenantId,PickupRequestId id,String courier,Instant now) {
  return change(tenantId,id,p->{p.assign(courier,now);});
 }
 public CompletionStage<PickupRequest> recordCollection(TenantId tenantId,PickupRequestId id,String evidence,Instant now) {
  return change(tenantId,id,p->{p.recordCollection(evidence,now);});
 }
 public CompletionStage<PickupRequest> recordFailure(TenantId tenantId,PickupRequestId id,String reason,Instant now) {
  return change(tenantId,id,p->{p.recordFailedAttempt(reason,now);});
 }
 private CompletionStage<PickupRequest> change(TenantId tenantId,PickupRequestId id,java.util.function.Consumer<PickupRequest> action) {
  return pickups.findById(tenantId,id).thenCompose(found->{
   if(found.isEmpty())return CompletableFuture.failedFuture(new IllegalArgumentException("pickup not found for tenant"));
   PickupRequest pickup=found.get();action.accept(pickup);return pickups.save(tenantId,pickup);
  });
 }
}