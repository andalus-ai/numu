package tech.kayys.numou.courier.adapter.postgres;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
/** PostgreSQL adapter with explicit tenant scoping and guarded state transitions. */
@ApplicationScoped
public class JdbcPickupRequestRepository implements PickupRequestRepository {
 private final DataSource dataSource;
 @Inject public JdbcPickupRequestRepository(DataSource dataSource){this.dataSource=dataSource;}
 @Override public CompletionStage<PickupRequest> save(TenantId tenant,PickupRequest p) {
  if(tenant==null||p==null)return CompletableFuture.failedFuture(new IllegalArgumentException("tenant and pickup required"));
  if(!tenant.equals(p.tenantId()))return CompletableFuture.failedFuture(new SecurityException("pickup tenant mismatch"));
  String sql="""
   INSERT INTO courier_pickup_requests(tenant_id,id,shipment_id,reference,merchant_reference,pickup_address,
    requested_window_start,requested_window_end,requested_by,status,created_at,updated_at,assigned_at,collected_at,
    courier_reference,collection_evidence_reference,failure_reason)
   VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
   ON CONFLICT(tenant_id,id) DO UPDATE SET status=EXCLUDED.status,updated_at=EXCLUDED.updated_at,
    assigned_at=EXCLUDED.assigned_at,collected_at=EXCLUDED.collected_at,courier_reference=EXCLUDED.courier_reference,
    collection_evidence_reference=EXCLUDED.collection_evidence_reference,failure_reason=EXCLUDED.failure_reason
   WHERE courier_pickup_requests.shipment_id=EXCLUDED.shipment_id
    AND courier_pickup_requests.reference=EXCLUDED.reference
    AND (courier_pickup_requests.status=EXCLUDED.status
     OR (courier_pickup_requests.status='REQUESTED' AND EXCLUDED.status IN ('ASSIGNED','CANCELLED'))
     OR (courier_pickup_requests.status='FAILED' AND EXCLUDED.status IN ('ASSIGNED','CANCELLED'))
     OR (courier_pickup_requests.status='ASSIGNED' AND EXCLUDED.status IN ('COLLECTED','FAILED','CANCELLED')))
   """;
  try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
   s.setObject(1,tenant.value());s.setObject(2,p.id().value());s.setObject(3,p.shipmentId().value());s.setString(4,p.reference());
   s.setString(5,p.merchantReference());s.setString(6,p.pickupAddress());s.setTimestamp(7,Timestamp.from(p.requestedWindowStart()));
   s.setTimestamp(8,Timestamp.from(p.requestedWindowEnd()));s.setString(9,p.requestedBy());s.setString(10,p.status().name());
   s.setTimestamp(11,Timestamp.from(p.createdAt()));s.setTimestamp(12,Timestamp.from(p.updatedAt()));
   setInstant(s,13,p.assignedAt());setInstant(s,14,p.collectedAt());s.setString(15,p.courierReference());
   s.setString(16,p.collectionEvidenceReference());s.setString(17,p.failureReason());
   if(s.executeUpdate()!=1)throw new SQLException("pickup state transition conflicted with persisted state");
   return CompletableFuture.completedFuture(p);
  }catch(SQLException e){return CompletableFuture.failedFuture(e);}
 }
 @Override public CompletionStage<Optional<PickupRequest>> findById(TenantId tenant,PickupRequestId id){
  if(tenant==null||id==null)return CompletableFuture.failedFuture(new IllegalArgumentException("tenant and id required"));
  String sql="SELECT * FROM courier_pickup_requests WHERE tenant_id=? AND id=?";
  try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
   s.setObject(1,tenant.value());s.setObject(2,id.value());
   try(ResultSet r=s.executeQuery()){
    if(!r.next())return CompletableFuture.completedFuture(Optional.empty());
    PickupRequest p=PickupRequest.reconstitute(PickupRequestId.of((java.util.UUID)r.getObject("id")),tenant,
     ShipmentId.of((java.util.UUID)r.getObject("shipment_id")),r.getString("reference"),r.getString("merchant_reference"),
     r.getString("pickup_address"),r.getTimestamp("requested_window_start").toInstant(),r.getTimestamp("requested_window_end").toInstant(),
     r.getString("requested_by"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),
     PickupRequestStatus.valueOf(r.getString("status")),instant(r,"assigned_at"),instant(r,"collected_at"),
     r.getString("courier_reference"),r.getString("collection_evidence_reference"),r.getString("failure_reason"));
    return CompletableFuture.completedFuture(Optional.of(p));
   }
  }catch(SQLException e){return CompletableFuture.failedFuture(e);}
 }
 private static Instant instant(ResultSet r,String name)throws SQLException{Timestamp t=r.getTimestamp(name);return t==null?null:t.toInstant();}
 private static void setInstant(PreparedStatement s,int i,Instant t)throws SQLException{if(t==null)s.setNull(i,Types.TIMESTAMP_WITH_TIMEZONE);else s.setTimestamp(i,Timestamp.from(t));}
}