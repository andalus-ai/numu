package tech.kayys.numou.event.adapter.postgres;
import tech.kayys.numou.event.application.replay.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.util.*;

/** PostgreSQL implementation of durable, tenant-scoped DLQ replay claims. */
public final class PostgresDeadLetterReplayOperations implements DeadLetterReplayOperations {
 private final DataSource ds;
 public PostgresDeadLetterReplayOperations(DataSource ds){this.ds=Objects.requireNonNull(ds);}
 public boolean capture(DeadLetterReplayRecord r){
  String q="INSERT INTO event_dead_letter_record(tenant_id,cluster_id,source_topic,source_partition,source_offset,event_type,payload,failure_reason,failed_at) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING";
  try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){bind(p,1,r.tenantId(),r.source());p.setString(6,r.eventType());p.setString(7,r.payload());p.setString(8,r.failureReason());p.setTimestamp(9,Timestamp.from(r.failedAt()));return p.executeUpdate()==1;}catch(SQLException e){throw fail(e);}
 }
 public Optional<DeadLetterReplayClaim> claim(UUID tenant,DeadLetterSourceKey s,String actor,String reason,Instant now,Duration lease){
  if(lease==null||lease.isNegative()||lease.isZero())throw new IllegalArgumentException("lease must be positive");
  UUID token=UUID.randomUUID();
  String q="UPDATE event_dead_letter_record SET replay_status='CLAIMED',claim_token=?,lease_until=?,claimed_by=?,replay_reason=?,replay_attempts=replay_attempts+1,updated_at=? WHERE tenant_id=? AND cluster_id=? AND source_topic=? AND source_partition=? AND source_offset=? AND replay_status<>'PUBLISHED' AND (replay_status IN ('PENDING','FAILED') OR lease_until<=?) RETURNING event_type,payload,failure_reason,failed_at";
  try(Connection c=ds.getConnection()){c.setAutoCommit(false);try{
   DeadLetterReplayRecord record;
   try(PreparedStatement p=c.prepareStatement(q)){p.setObject(1,token);p.setTimestamp(2,Timestamp.from(now.plus(lease)));p.setString(3,actor);p.setString(4,reason);p.setTimestamp(5,Timestamp.from(now));bind(p,6,tenant,s);p.setTimestamp(11,Timestamp.from(now));try(ResultSet r=p.executeQuery()){if(!r.next()){c.commit();return Optional.empty();}record=new DeadLetterReplayRecord(tenant,s,r.getString(1),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant());}}
   audit(c,tenant,s,token,actor,reason,"CLAIMED",null,now);c.commit();return Optional.of(new DeadLetterReplayClaim(token,record));
  }catch(Exception e){rollback(c,e);throw e;}}catch(SQLException e){throw fail(e);}
 }
 public boolean markPublished(UUID t,DeadLetterSourceKey s,UUID token,Instant at){return finish(t,s,token,at,"PUBLISHED",null);}
 public boolean markFailed(UUID t,DeadLetterSourceKey s,UUID token,String error,Instant at){return finish(t,s,token,at,"FAILED",bounded(error,1000));}
 private boolean finish(UUID t,DeadLetterSourceKey s,UUID token,Instant at,String outcome,String detail){
  String q="UPDATE event_dead_letter_record SET replay_status=?,claim_token=NULL,lease_until=NULL,published_at=CASE WHEN ?='PUBLISHED' THEN ? ELSE published_at END,last_error=?,updated_at=? WHERE tenant_id=? AND cluster_id=? AND source_topic=? AND source_partition=? AND source_offset=? AND replay_status='CLAIMED' AND claim_token=? RETURNING claimed_by,replay_reason";
  try(Connection c=ds.getConnection()){c.setAutoCommit(false);try{String actor=null,reason=null;try(PreparedStatement p=c.prepareStatement(q)){p.setString(1,outcome);p.setString(2,outcome);p.setTimestamp(3,Timestamp.from(at));p.setString(4,detail);p.setTimestamp(5,Timestamp.from(at));bind(p,6,t,s);p.setObject(11,token);try(ResultSet r=p.executeQuery()){if(r.next()){actor=r.getString(1);reason=r.getString(2);}}}boolean ok=actor!=null;if(ok)audit(c,t,s,token,actor,reason,outcome,detail,at);c.commit();return ok;}catch(Exception e){rollback(c,e);throw e;}}catch(SQLException e){throw fail(e);}
 }
 private static void audit(Connection c,UUID t,DeadLetterSourceKey s,UUID token,String actor,String reason,String outcome,String detail,Instant at)throws SQLException{
  String q="INSERT INTO event_dead_letter_replay_audit(tenant_id,cluster_id,source_topic,source_partition,source_offset,claim_token,actor_id,reason,outcome,detail,occurred_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
  try(PreparedStatement p=c.prepareStatement(q)){bind(p,1,t,s);p.setObject(6,token);p.setString(7,actor);p.setString(8,reason);p.setString(9,outcome);p.setString(10,detail);p.setTimestamp(11,Timestamp.from(at));p.executeUpdate();}
 }
 private static void bind(PreparedStatement p,int i,UUID t,DeadLetterSourceKey s)throws SQLException{p.setObject(i,t);p.setString(i+1,s.clusterId());p.setString(i+2,s.topic());p.setInt(i+3,s.partition());p.setLong(i+4,s.offset());}
 private static void rollback(Connection c,Exception e){try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}}
 private static String bounded(String v,int max){Objects.requireNonNull(v);String x=v.trim();if(x.isEmpty()||x.length()>max)throw new IllegalArgumentException("invalid detail");return x;}
 private static RuntimeException fail(SQLException e){return new IllegalStateException("PostgreSQL DLQ replay operation failed",e);}
}