package tech.kayys.numou.affiliate.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class PostgresAffiliateOutboxRepository implements AffiliateEventOutboxStore {
 private final DataSource dataSource;
 @Inject public PostgresAffiliateOutboxRepository(DataSource ds){this.dataSource=ds;}

 @Override public CompletionStage<List<AffiliateEventOutboxStore.ClaimedEvent>> claimPending(
         String tenant,String worker,Instant now,Duration lease,int limit){
  if(tenant==null||tenant.isBlank()||worker==null||worker.isBlank()||lease==null||lease.isNegative()||lease.isZero()||limit<1||limit>500)
   return CompletableFuture.failedFuture(new IllegalArgumentException("invalid tenant, worker, lease, or limit"));
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{
   String q="WITH candidates AS (SELECT outbox_id FROM affiliate_event_outbox WHERE tenant_id=? AND available_at<=? AND (delivery_status='PENDING' OR (delivery_status='IN_FLIGHT' AND lease_until<=?)) ORDER BY occurred_at,outbox_id LIMIT ? FOR UPDATE SKIP LOCKED) UPDATE affiliate_event_outbox o SET delivery_status='IN_FLIGHT',lease_owner=?,lease_until=?,attempts=attempts+1,version=version+1 FROM candidates x WHERE o.outbox_id=x.outbox_id RETURNING o.outbox_id,o.aggregate_type,o.aggregate_id,o.event_type,o.event_key,o.payload::text,o.attempts,o.lease_until,o.occurred_at";
   List<AffiliateEventOutboxStore.ClaimedEvent> result=new ArrayList<>();
   try(PreparedStatement s=c.prepareStatement(q)){
    s.setString(1,tenant);s.setTimestamp(2,Timestamp.from(now));s.setTimestamp(3,Timestamp.from(now));
    s.setInt(4,limit);s.setString(5,worker);s.setTimestamp(6,Timestamp.from(now.plus(lease)));
    try(ResultSet r=s.executeQuery()){while(r.next())result.add(new AffiliateEventOutboxStore.ClaimedEvent(
      tenant,r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getString(4),
      r.getString(5),r.getString(6),r.getInt(7),worker,r.getTimestamp(8).toInstant(),r.getTimestamp(9).toInstant()));}
   }
   c.commit();return CompletableFuture.completedFuture(List.copyOf(result));
  }catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}throw e;}
  finally{c.setAutoCommit(true);}
  }catch(SQLException e){return CompletableFuture.failedFuture(failure(e));}catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
 }

 @Override public CompletionStage<Void> markDelivered(String tenant,UUID id,String worker,Instant now){
  return update("UPDATE affiliate_event_outbox SET delivery_status='DELIVERED',delivered_at=?,lease_owner=NULL,lease_until=NULL,last_error=NULL,version=version+1 WHERE tenant_id=? AND outbox_id=? AND delivery_status='IN_FLIGHT' AND lease_owner=? AND lease_until>?",
   s->{s.setTimestamp(1,Timestamp.from(now));s.setString(2,tenant);s.setObject(3,id);s.setString(4,worker);s.setTimestamp(5,Timestamp.from(now));});
 }

 @Override public CompletionStage<Void> reschedule(String tenant,UUID id,String worker,String reason,Instant retryAt,Instant now,int maxAttempts){
  if(reason==null||reason.isBlank()||reason.length()>1000||maxAttempts<1)return CompletableFuture.failedFuture(new IllegalArgumentException("invalid retry details"));
  return update("UPDATE affiliate_event_outbox SET delivery_status=CASE WHEN attempts>=? THEN 'DEAD_LETTER' ELSE 'PENDING' END,available_at=?,lease_owner=NULL,lease_until=NULL,last_error=?,version=version+1 WHERE tenant_id=? AND outbox_id=? AND delivery_status='IN_FLIGHT' AND lease_owner=? AND lease_until>?",
   s->{s.setInt(1,maxAttempts);s.setTimestamp(2,Timestamp.from(retryAt));s.setString(3,reason);s.setString(4,tenant);s.setObject(5,id);s.setString(6,worker);s.setTimestamp(7,Timestamp.from(now));});
 }

 @Override public CompletionStage<Boolean> redriveDeadLetter(String tenant,UUID id,String actor,String reason,String idempotencyKey,String correlationId,Instant now){
  if(tenant==null||tenant.isBlank()||actor==null||actor.isBlank()||reason==null||reason.isBlank()||reason.length()>1000||idempotencyKey==null||idempotencyKey.isBlank()||correlationId==null||correlationId.isBlank())
   return CompletableFuture.failedFuture(new IllegalArgumentException("invalid dead-letter redrive context"));
  String updateSql="UPDATE affiliate_event_outbox SET delivery_status='PENDING',available_at=?,attempts=0,lease_owner=NULL,lease_until=NULL,last_error=NULL,version=version+1 WHERE tenant_id=? AND outbox_id=? AND delivery_status='DEAD_LETTER'";
  String auditSql="INSERT INTO affiliate_audit_event(event_id,tenant_id,aggregate_type,aggregate_id,event_type,actor_id,correlation_id,idempotency_key,payload,occurred_at) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?)";
  try(Connection c=dataSource.getConnection()){
   c.setAutoCommit(false);
   try(PreparedStatement u=c.prepareStatement(updateSql)){
    u.setTimestamp(1,Timestamp.from(now));u.setString(2,tenant);u.setObject(3,id);
    if(u.executeUpdate()!=1){c.rollback();return CompletableFuture.completedFuture(false);}
    try(PreparedStatement a=c.prepareStatement(auditSql)){
     a.setObject(1,UUID.randomUUID());a.setString(2,tenant);a.setString(3,"AFFILIATE_OUTBOX");a.setObject(4,id);
     a.setString(5,"AffiliateOutboxDeadLetterRedriven");a.setString(6,actor);a.setString(7,correlationId);
     a.setString(8,idempotencyKey);a.setString(9,"{\"reasonBase64\":\""+Base64.getEncoder().encodeToString(reason.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"\"}");
     a.setTimestamp(10,Timestamp.from(now));a.executeUpdate();
    }
    c.commit();return CompletableFuture.completedFuture(true);
   }catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}throw e;}
   finally{try{c.setAutoCommit(true);}catch(SQLException ignored){}}
  }catch(SQLException e){return CompletableFuture.failedFuture(failure(e));}
 }

 @Override public CompletionStage<AffiliateEventOutboxStore.DeadLetterPage> findDeadLetters(
         String tenant, AffiliateEventOutboxStore.DeadLetterCursor before, int limit) {
  if (tenant == null || tenant.isBlank() || limit < 1 || limit > 100) {
   return CompletableFuture.failedFuture(new IllegalArgumentException("invalid tenant or dead-letter page size"));
  }
  String sql = "SELECT outbox_id,aggregate_type,aggregate_id,event_type,attempts,occurred_at,version "
      + "FROM affiliate_event_outbox WHERE tenant_id=? AND delivery_status='DEAD_LETTER' "
      + (before == null ? "" : "AND (occurred_at,outbox_id) < (?,?) ")
      + "ORDER BY occurred_at DESC,outbox_id DESC LIMIT ?";
  try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
   int index = 1;
   s.setString(index++, tenant);
   if (before != null) {
    s.setTimestamp(index++, Timestamp.from(before.occurredAt()));
    s.setObject(index++, before.eventId());
   }
   s.setInt(index, limit + 1);
   List<AffiliateEventOutboxStore.DeadLetterSummary> items = new ArrayList<>();
   try (ResultSet r = s.executeQuery()) {
    while (r.next()) {
     items.add(new AffiliateEventOutboxStore.DeadLetterSummary(
         r.getObject(1, UUID.class), r.getString(2), r.getObject(3, UUID.class),
         r.getString(4), r.getInt(5), r.getTimestamp(6).toInstant(), r.getLong(7)));
    }
   }
   boolean hasMore = items.size() > limit;
   if (hasMore) items.remove(items.size() - 1);
   AffiliateEventOutboxStore.DeadLetterCursor next = null;
   if (hasMore && !items.isEmpty()) {
    AffiliateEventOutboxStore.DeadLetterSummary last = items.get(items.size() - 1);
    next = new AffiliateEventOutboxStore.DeadLetterCursor(last.occurredAt(), last.eventId());
   }
   return CompletableFuture.completedFuture(new AffiliateEventOutboxStore.DeadLetterPage(items, next));
  } catch (SQLException e) {
   return CompletableFuture.failedFuture(failure(e));
  }
 }

 @Override public CompletionStage<AffiliateEventOutboxStore.OutboxHealthSnapshot> healthSnapshot() {
  String sql = """
      SELECT
        count(*) FILTER (WHERE delivery_status='PENDING') AS pending,
        count(*) FILTER (WHERE delivery_status='PENDING' AND available_at<=clock_timestamp()) AS pending_due,
        count(*) FILTER (WHERE delivery_status='IN_FLIGHT') AS in_flight,
        count(*) FILTER (WHERE delivery_status='IN_FLIGHT' AND lease_until<=clock_timestamp()) AS expired_leases,
        count(*) FILTER (WHERE delivery_status='DEAD_LETTER') AS dead_letter,
        COALESCE(EXTRACT(EPOCH FROM
          (clock_timestamp() - (min(available_at) FILTER
            (WHERE delivery_status='PENDING' AND available_at<=clock_timestamp())))), 0)::bigint
          AS oldest_due_age_seconds
      FROM affiliate_event_outbox
      """;
  try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql);
       ResultSet r = s.executeQuery()) {
   if (!r.next()) throw new IllegalStateException("outbox health query returned no row");
   return CompletableFuture.completedFuture(new AffiliateEventOutboxStore.OutboxHealthSnapshot(
       r.getLong("pending"), r.getLong("pending_due"), r.getLong("in_flight"),
       r.getLong("expired_leases"), r.getLong("dead_letter"), r.getLong("oldest_due_age_seconds")));
  } catch (SQLException e) {
   return CompletableFuture.failedFuture(failure(e));
  } catch (RuntimeException e) {
   return CompletableFuture.failedFuture(e);
  }
 }

 private CompletionStage<Void> update(String sql,Binder binder){
  try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(sql)){
   binder.bind(s);if(s.executeUpdate()!=1)throw new IllegalStateException("Outbox lease is stale, expired, or owned by another worker");
   return CompletableFuture.completedFuture(null);
  }catch(SQLException e){return CompletableFuture.failedFuture(failure(e));}
  catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
 }
 private static IllegalStateException failure(SQLException e){return new IllegalStateException("Affiliate outbox persistence failed",e);}
 @FunctionalInterface private interface Binder{void bind(PreparedStatement s)throws SQLException;}
}
