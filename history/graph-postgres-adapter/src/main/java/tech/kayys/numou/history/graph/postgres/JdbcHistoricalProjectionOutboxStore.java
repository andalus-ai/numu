package tech.kayys.numou.history.graph.postgres;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.*;
import javax.sql.DataSource; import java.sql.*; import java.time.Instant; import java.util.*;
/** PostgreSQL outbox delivery using SKIP LOCKED and token-fenced state transitions. */
public final class JdbcHistoricalProjectionOutboxStore implements HistoricalProjectionOutboxStore {
 private final DataSource source; public JdbcHistoricalProjectionOutboxStore(DataSource source){this.source=Objects.requireNonNull(source);}
 public List<ClaimedProjectionEvent> claimDue(String worker,Instant now,Instant until,int limit) {
  Objects.requireNonNull(now);Objects.requireNonNull(until);
  if(worker==null||worker.isBlank()||worker.length()>160||!until.isAfter(now)||limit<1||limit>500)throw new IllegalArgumentException("invalid claim arguments");
  String sql="""
   WITH candidates AS (SELECT event_id FROM historical_relationship_projection_outbox
    WHERE published_at IS NULL AND dead_lettered_at IS NULL AND available_at<=?
      AND (lease_until IS NULL OR lease_until<=?) ORDER BY available_at,created_at,event_id
    LIMIT ? FOR UPDATE SKIP LOCKED)
   UPDATE historical_relationship_projection_outbox o SET lease_owner=?,lease_until=?,lease_token=gen_random_uuid(),
    attempts=attempts+1,retry_attempts=retry_attempts+1 FROM candidates c WHERE o.event_id=c.event_id
   RETURNING o.event_id,o.event_key,o.event_type,o.schema_version,o.tenant_id,o.aggregate_id,o.aggregate_revision,
    o.payload::text,o.occurred_at,o.attempts,o.retry_attempts,o.lease_token,o.lease_until
   """;
  List<ClaimedProjectionEvent> out=new ArrayList<>();
  try(Connection c=source.getConnection()){c.setAutoCommit(false);try(PreparedStatement s=c.prepareStatement(sql)){
   s.setTimestamp(1,Timestamp.from(now));s.setTimestamp(2,Timestamp.from(now));s.setInt(3,limit);s.setString(4,worker);s.setTimestamp(5,Timestamp.from(until));
   try(ResultSet r=s.executeQuery()){while(r.next())out.add(new ClaimedProjectionEvent(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),r.getObject(5,UUID.class),r.getObject(6,UUID.class),r.getLong(7),r.getString(8),r.getTimestamp(9).toInstant(),r.getInt(10),r.getInt(11),r.getObject(12,UUID.class),r.getTimestamp(13).toInstant()));}
   c.commit();return List.copyOf(out);
  }catch(SQLException|RuntimeException e){rollback(c,e);throw e;}}catch(SQLException e){throw failure("claim projection events",e);}
 }
 public boolean markPublished(UUID id,UUID token,Instant now){return update("UPDATE historical_relationship_projection_outbox SET published_at=?,lease_owner=NULL,lease_until=NULL,lease_token=NULL WHERE event_id=? AND lease_token=? AND published_at IS NULL AND dead_lettered_at IS NULL",s->{s.setTimestamp(1,Timestamp.from(now));s.setObject(2,id);s.setObject(3,token);});}
 public boolean scheduleRetry(UUID id,UUID token,Instant at,String error,Instant now){return update("UPDATE historical_relationship_projection_outbox SET available_at=?,last_error=?,lease_owner=NULL,lease_until=NULL,lease_token=NULL WHERE event_id=? AND lease_token=? AND published_at IS NULL AND dead_lettered_at IS NULL",s->{s.setTimestamp(1,Timestamp.from(at));s.setString(2,safe(error));s.setObject(3,id);s.setObject(4,token);});}
 public boolean markDead(UUID id,UUID token,String error,Instant now){return update("UPDATE historical_relationship_projection_outbox SET dead_lettered_at=?,last_error=?,lease_owner=NULL,lease_until=NULL,lease_token=NULL WHERE event_id=? AND lease_token=? AND published_at IS NULL AND dead_lettered_at IS NULL",s->{s.setTimestamp(1,Timestamp.from(now));s.setString(2,safe(error));s.setObject(3,id);s.setObject(4,token);});}
 public List<DeadProjectionEvent> listDead(int limit) {
  if(limit<1||limit>200)throw new IllegalArgumentException("limit must be 1..200");
  String q="SELECT event_id,event_key,event_type,schema_version,tenant_id,aggregate_id,aggregate_revision,payload::text,attempts,retry_attempts,last_error,dead_lettered_at FROM historical_relationship_projection_outbox WHERE dead_lettered_at IS NOT NULL ORDER BY dead_lettered_at,event_id LIMIT ?";
  List<DeadProjectionEvent> out=new ArrayList<>();try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement(q)){s.setInt(1,limit);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new DeadProjectionEvent(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),r.getObject(5,UUID.class),r.getObject(6,UUID.class),r.getLong(7),r.getString(8),r.getInt(9),r.getInt(10),r.getString(11),r.getTimestamp(12).toInstant()));}return List.copyOf(out);}catch(SQLException e){throw failure("list dead projection events",e);}
 }
 public boolean requeueDead(UUID id,UUID actor,Instant now) {
  Objects.requireNonNull(id);Objects.requireNonNull(actor);Objects.requireNonNull(now);
  try(Connection c=source.getConnection()){c.setAutoCommit(false);try{
   int attempts;try(PreparedStatement s=c.prepareStatement("SELECT attempts FROM historical_relationship_projection_outbox WHERE event_id=? AND dead_lettered_at IS NOT NULL FOR UPDATE")){s.setObject(1,id);try(ResultSet r=s.executeQuery()){if(!r.next()){c.commit();return false;}attempts=r.getInt(1);}}
   try(PreparedStatement s=c.prepareStatement("INSERT INTO historical_relationship_projection_replay_audit(event_id,actor_id,lifetime_attempts,replayed_at) VALUES(?,?,?,?)")){s.setObject(1,id);s.setObject(2,actor);s.setInt(3,attempts);s.setTimestamp(4,Timestamp.from(now));s.executeUpdate();}
   try(PreparedStatement s=c.prepareStatement("UPDATE historical_relationship_projection_outbox SET dead_lettered_at=NULL,available_at=?,retry_attempts=0,lease_owner=NULL,lease_until=NULL,lease_token=NULL WHERE event_id=? AND dead_lettered_at IS NOT NULL")){s.setTimestamp(1,Timestamp.from(now));s.setObject(2,id);boolean changed=s.executeUpdate()==1;c.commit();return changed;}
  }catch(SQLException|RuntimeException e){rollback(c,e);throw e;}}catch(SQLException e){throw failure("requeue dead projection event",e);}
 }
 private boolean update(String q,Binder b){try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement(q)){b.bind(s);return s.executeUpdate()==1;}catch(SQLException e){throw failure("update projection delivery state",e);}}
 private static String safe(String e){String s=Objects.toString(e,"projection failed").replaceAll("[\\r\\n\\t]+"," ");return s.length()>2000?s.substring(0,2000):s;}
 private static void rollback(Connection c,Throwable t){try{c.rollback();}catch(SQLException e){t.addSuppressed(e);}}
 private static IllegalStateException failure(String op,SQLException e){return new IllegalStateException("Unable to "+op,e);}
 @FunctionalInterface private interface Binder{void bind(PreparedStatement s)throws SQLException;}
}