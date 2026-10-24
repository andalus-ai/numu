package tech.kayys.numou.courier.domain;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
/** Tenant-owned first-mile pickup request. Evidence is an opaque protected-media reference. */
public final class PickupRequest {
 private final PickupRequestId id; private final TenantId tenantId; private final ShipmentId shipmentId;
 private final String reference, merchantReference, pickupAddress, requestedBy;
 private final Instant requestedWindowStart, requestedWindowEnd, createdAt;
 private PickupRequestStatus status=PickupRequestStatus.REQUESTED;
 private Instant updatedAt, assignedAt, collectedAt;
 private String courierReference, collectionEvidenceReference, failureReason;
 private PickupRequest(PickupRequestId id,TenantId tenantId,ShipmentId shipmentId,String reference,
   String merchantReference,String pickupAddress,Instant start,Instant end,String requestedBy,Instant now) {
  this.id=Objects.requireNonNull(id);this.tenantId=Objects.requireNonNull(tenantId);this.shipmentId=Objects.requireNonNull(shipmentId);
  this.reference=required(reference,80,"reference");this.merchantReference=required(merchantReference,160,"merchantReference");
  this.pickupAddress=required(pickupAddress,1000,"pickupAddress");this.requestedBy=required(requestedBy,160,"requestedBy");
  this.requestedWindowStart=Objects.requireNonNull(start);this.requestedWindowEnd=Objects.requireNonNull(end);
  if(!end.isAfter(start))throw new IllegalArgumentException("pickup window must have positive duration");
  this.createdAt=Objects.requireNonNull(now);this.updatedAt=now;
 }
 public static PickupRequest request(PickupRequestId id,TenantId tenantId,ShipmentId shipmentId,String reference,
   String merchantReference,String address,Instant start,Instant end,String requestedBy,Instant now) {
  Objects.requireNonNull(now);if(start==null||start.isBefore(now))throw new IllegalArgumentException("pickup window cannot start in the past");
  return new PickupRequest(id,tenantId,shipmentId,reference,merchantReference,address,start,end,requestedBy,now);
 }
 public void assign(String courier,Instant now) {
  if(status!=PickupRequestStatus.REQUESTED&&status!=PickupRequestStatus.FAILED)throw new IllegalStateException("pickup is not assignable");
  courierReference=required(courier,160,"courierReference");assignedAt=monotonic(now);updatedAt=assignedAt;
  failureReason=null;status=PickupRequestStatus.ASSIGNED;
 }
 public void recordCollection(String evidence,Instant now) {
  require(PickupRequestStatus.ASSIGNED);collectionEvidenceReference=required(evidence,512,"evidenceReference");
  collectedAt=monotonic(now);updatedAt=collectedAt;status=PickupRequestStatus.COLLECTED;
 }
 public void recordFailedAttempt(String reason,Instant now) {
  require(PickupRequestStatus.ASSIGNED);failureReason=required(reason,500,"failureReason");
  updatedAt=monotonic(now);status=PickupRequestStatus.FAILED;
 }
 public void cancel(Instant now) {
  if(status==PickupRequestStatus.CANCELLED)return;
  if(status!=PickupRequestStatus.REQUESTED&&status!=PickupRequestStatus.ASSIGNED&&status!=PickupRequestStatus.FAILED)
   throw new IllegalStateException("collected pickup cannot be cancelled");
  updatedAt=monotonic(now);status=PickupRequestStatus.CANCELLED;
 }
 public static PickupRequest reconstitute(PickupRequestId id,TenantId tenantId,ShipmentId shipmentId,String reference,
  String merchantReference,String address,Instant start,Instant end,String requestedBy,Instant createdAt,Instant updatedAt,
  PickupRequestStatus status,Instant assignedAt,Instant collectedAt,String courier,String evidence,String failure) {
  PickupRequest p=new PickupRequest(id,tenantId,shipmentId,reference,merchantReference,address,start,end,requestedBy,createdAt);
  if(updatedAt==null||updatedAt.isBefore(createdAt))throw new IllegalArgumentException("invalid updatedAt");
  p.updatedAt=updatedAt;p.status=Objects.requireNonNull(status);
  if((status==PickupRequestStatus.ASSIGNED||status==PickupRequestStatus.COLLECTED||status==PickupRequestStatus.FAILED)
    &&(courier==null||assignedAt==null))throw new IllegalArgumentException("assigned state requires courier and timestamp");
  if(status==PickupRequestStatus.COLLECTED&&(collectedAt==null||evidence==null))throw new IllegalArgumentException("collection requires evidence");
  if(assignedAt!=null&&(assignedAt.isBefore(createdAt)||assignedAt.isAfter(updatedAt)))throw new IllegalArgumentException("invalid assignedAt");
  if(collectedAt!=null&&(assignedAt==null||collectedAt.isBefore(assignedAt)||collectedAt.isAfter(updatedAt)))throw new IllegalArgumentException("invalid collectedAt");
  if(status==PickupRequestStatus.FAILED&&(failure==null||failure.isBlank()))throw new IllegalArgumentException("failure reason required");
  p.assignedAt=assignedAt;p.collectedAt=collectedAt;p.courierReference=courier;p.collectionEvidenceReference=evidence;p.failureReason=failure;
  return p;
 }
 private Instant monotonic(Instant now){Objects.requireNonNull(now);if(now.isBefore(updatedAt))throw new IllegalArgumentException("time must not move backwards");return now;}
 private void require(PickupRequestStatus expected){if(status!=expected)throw new IllegalStateException("expected "+expected+" but was "+status);}
 private static String required(String s,int max,String field){Objects.requireNonNull(s,field);String v=s.trim();if(v.isEmpty()||v.length()>max)throw new IllegalArgumentException(field+" must contain 1-"+max+" characters");return v;}
 public PickupRequestId id(){return id;} public TenantId tenantId(){return tenantId;} public ShipmentId shipmentId(){return shipmentId;}
 public String reference(){return reference;} public String merchantReference(){return merchantReference;} public String pickupAddress(){return pickupAddress;}
 public String requestedBy(){return requestedBy;} public Instant requestedWindowStart(){return requestedWindowStart;} public Instant requestedWindowEnd(){return requestedWindowEnd;}
 public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;} public PickupRequestStatus status(){return status;}
 public Instant assignedAt(){return assignedAt;} public Instant collectedAt(){return collectedAt;} public String courierReference(){return courierReference;}
 public String collectionEvidenceReference(){return collectionEvidenceReference;} public String failureReason(){return failureReason;}
}