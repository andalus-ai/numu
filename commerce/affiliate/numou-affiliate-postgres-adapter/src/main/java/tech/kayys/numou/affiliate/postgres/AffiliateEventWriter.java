package tech.kayys.numou.affiliate.postgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
/** Writes the immutable audit fact and dispatch intent on the caller's transaction. */
final class AffiliateEventWriter {
 private AffiliateEventWriter() {}
 static void append(Connection c,String tenant,String type,UUID id,String event,String actor,String correlation,
                    String key,String payload,Instant at) throws SQLException {
  UUID eventId=UUID.nameUUIDFromBytes((tenant+":"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  String a="INSERT INTO affiliate_audit_event (event_id,tenant_id,aggregate_type,aggregate_id,event_type,actor_id,correlation_id,idempotency_key,payload,occurred_at) VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT (tenant_id,event_id) DO NOTHING";
  try(PreparedStatement s=c.prepareStatement(a)){s.setObject(1,eventId);s.setString(2,tenant);s.setString(3,type);s.setObject(4,id);s.setString(5,event);s.setString(6,actor==null||actor.isBlank()?"SYSTEM":actor);s.setString(7,correlation);s.setString(8,key);s.setString(9,payload);s.setTimestamp(10,Timestamp.from(at));s.executeUpdate();}
  String o="INSERT INTO affiliate_event_outbox (outbox_id,tenant_id,aggregate_type,aggregate_id,event_type,event_key,payload,occurred_at) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?) ON CONFLICT (tenant_id,event_key) DO NOTHING";
  try(PreparedStatement s=c.prepareStatement(o)){s.setObject(1,eventId);s.setString(2,tenant);s.setString(3,type);s.setObject(4,id);s.setString(5,event);s.setString(6,key);s.setString(7,payload);s.setTimestamp(8,Timestamp.from(at));s.executeUpdate();}
 }
 static String json(String status,long version){return "{\"status\":\""+status+"\",\"version\":"+version+"}";}
 static String json(String status){return "{\"status\":\""+status+"\"}";}
}