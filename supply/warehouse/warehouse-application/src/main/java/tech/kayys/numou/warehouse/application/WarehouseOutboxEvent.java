package tech.kayys.numou.warehouse.application;
import java.time.Instant;
import java.util.UUID;
/** Persist this event atomically with warehouse state and command receipt. */
public record WarehouseOutboxEvent(String eventId,String tenantId,String aggregateId,long aggregateVersion,String eventType,String payload,Instant occurredAt) {
 public static WarehouseOutboxEvent changed(String tenant,String id,long version,String type,String from,String to){String payload="{\"type\":\""+type+"\",\"previousStatus\":\""+from+"\",\"nextStatus\":\""+to+"\"}";return new WarehouseOutboxEvent(UUID.randomUUID().toString(),tenant,id,version,"warehouse.work-state-changed.v1",payload,Instant.now());}
}