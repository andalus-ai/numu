package tech.kayys.numou.affiliate.postgres;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
/** Tenant-scoped leased dispatcher queue. Delivery is at-least-once; consumers must be idempotent. */
@ApplicationScoped
public class PostgresAffiliateOutboxRepository {
 private final DataSource dataSource;
 @Inject public PostgresAffiliateOutboxRepository(DataSource ds){this.dataSource=ds;}
 public CompletionStage<List<ClaimedEvent>> claimPending(String tenant,String worker,Instant now,Duration lease,int limit){
  if(tenant==null||tenant.isBlank()||worker==null||worker.isBlank()||lease==null||lease.isNegative()||lease.isZero()||limit<1||limit>500)
   return CompletableFuture.failedFuture(new IllegalArgumentException("invalid tenant, worker, lease, or limit"));
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{
   String q="WITH candidates AS (SELECT outbox_id FROM affiliate_event_outbox WHERE tenant_id=? AND available_at<=? AND (delivery_status='PENDING' OR (delivery_status='IN_FLIGHT' AND lease_until<=?)) ORDER BY occurred_at,outbox_id FOR UPDATE SKIP LOCKED LIMIT ?) UPDATE affiliate_event_outbox o SET delivery_status='IN_FLIGHT',lease_owner=?,lease_until=?,attempts=attempts+1,version=version+1 FROM candidates x WHERE o.outbox_id=x.outbox_id RETURNING o.outbox_id,o.aggregate_type,o.aggregate_id,o.event_type,o.event_key,o.payload::text,o.attempts,o.lease_until";
   List<ClaimedEvent> result=new ArrayList<>();try(PreparedStatement s=c.prepareStatement(q)){s.setString(1,tenant);s.setTimestamp(2,Timestamp.from(now));s.setTimestamp(3,Timestamp.from(now));s.setInt(4,limit);s.setString(5,worker);s.setTimestamp(6,Timestamp.from(now.plus(lease)));try(ResultSet r=s.executeQuery()){while(r.next())result.add(new ClaimedEvent(tenant,r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getString(6),r.getInt(7),worker,r.getTimestamp(8).toInstant()));}}
   c.commit();return CompletableFuture.completedFuture(List.copyOf(result));
  }catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}throw e;}finally{c.setAutoCommit(true);}
  }catch(SQLException e){return CompletableFuture.failedFuture(failure(e));}catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
 }
 public CompletionStage<Void> markDelivered(String tenant,UUID id,String worker,Instant now){
  return update("UPDATE affiliate_event_outbox SET delivery_status='DELIVERED',delivered_at=?,lease_owner=NULL,lease_until=NULL,last_error=NULL,version=version+1 WHERE tenant_id=? AND outbox_id=? AND delivery_status='IN_FLIGHT' AND lease_owner=? AND lease_until>?",
   s->{s.setTimestamp(1,Timestamp.from(now));s.setString(2,tenant);s.setObject(3,id);s.setString(4,worker);s.setTimestamp(5,Timestamp.from(now));});
 }
 public CompletionStage<Void> reschedule(String tenant,UUID id,String worker,String reason,Instant retryAt,Instant now,int maxAttempts){
  if(reason==null||reason.isBlank()||reason.length()>1000||maxAttempts<1)return CompletableFuture.failedFuture(new IllegalArgumentException("invalid retry details"));
  return update("UPDATE affiliate_event_outbox SET delivery_status=CASE WHEN attempts>=? THEN 'DEAD_LETTER' ELSE 'PENDING' END,available_at=?,lease_owner=NULL,lease_until=NULL,last_error=?,version=version+1 WHERE tenant_id=? AND outbox_id=? AND delivery_status='IN_FLIGHT' AND lease_owner=? AND lease_until>?",
   s->{s.setInt(1,maxAttempts);s.setTimestamp(2,Timestamp.from(retryAt));s.setString(3,reason);s.setString(4,tenant);s.setObject(5,id);s.setString(6,worker);s.setTimestamp(7,Timestamp.from(now));});
 }
 private CompletionStage<Void> update(String sql,Binder binder){try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){binder.bind(s);if(s.executeUpdate()!=1)throw new IllegalStateException("Outbox lease is stale, expired, or owned by another worker");return CompletableFuture.completedFuture(null);}catch(SQLException e){return CompletableFuture.failedFuture(failure(e));}catch(RuntimeException e){return CompletableFuture.failedFuture(e);}}
 private static IllegalStateException failure(SQLException e){return new IllegalStateException("Affiliate outbox persistence failed",e);}
 @FunctionalInterface private interface Binder{void bind(PreparedStatement s)throws SQLException;}
 public record ClaimedEvent(String tenantId,UUID id,String aggregateType,UUID aggregateId,String eventType,String eventKey,String payload,int attempts,String leaseOwner,Instant leaseUntil){}
}