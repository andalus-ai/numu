package tech.kayys.numou.developer.apicatalog.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.sql.*;
import java.util.*;

@ApplicationScoped
public final class PostgresApiCatalogEventPublisher implements EventPublisher {
 private static final String INSERT="""
  INSERT INTO platform_outbox(entry_id,event_id,tenant_id,event_type,payload,created_at,available_at)
  VALUES(?,?,?,?,?::jsonb,?,?) ON CONFLICT(event_id) DO NOTHING
  """;
 private final ApiCatalogJdbcTransactionManager transactions;
 private final ExecutionContextProvider contexts;
 private final ObjectMapper mapper;
 @Inject public PostgresApiCatalogEventPublisher(ApiCatalogJdbcTransactionManager transactions,ExecutionContextProvider contexts,ObjectMapper mapper){
  this.transactions=Objects.requireNonNull(transactions);this.contexts=Objects.requireNonNull(contexts);this.mapper=Objects.requireNonNull(mapper);
 }
 @Override public Uni<Void> publish(List<DomainEvent> events){
  List<DomainEvent> safe=List.copyOf(Objects.requireNonNull(events));
  if(safe.isEmpty())return Uni.createFrom().voidItem();
  ExecutionContext context=Objects.requireNonNull(contexts.current());
  TenantId tenant=context.requireTenant();
  return transactions.withTenant(tenant,c->{for(DomainEvent event:safe)append(c,tenant,context,event);return null;});
 }
 private void append(Connection c,TenantId tenant,ExecutionContext context,DomainEvent event)throws SQLException{
  ObjectNode root=mapper.createObjectNode();
  root.put("specVersion","1");root.put("eventId",event.eventId().toString());root.put("eventType",event.eventType());
  root.put("eventVersion",event.eventVersion().toString());root.put("correlationId",context.correlationId().value());
  root.put("causationId",context.causationId().value());root.put("tenantId",tenant.value().toString());
  root.putNull("participantId");root.put("actorId",context.actorId().value());root.put("occurredAt",event.occurredAt().toString());root.put("traceId",context.traceId());
  JsonNode data=mapper.valueToTree(event);
  if(data instanceof ObjectNode object){object.remove("eventId");object.remove("occurredAt");}
  ObjectNode payload=mapper.createObjectNode();payload.put("eventId",event.eventId().toString());
  payload.put("occurredAtIso",event.occurredAt().toString());payload.put("eventType",event.eventType());payload.set("data",data);
  root.set("payload",payload);
  final String json;
  try{json=mapper.writeValueAsString(root);}catch(JsonProcessingException e){throw new IllegalArgumentException("Cannot serialize API catalog event "+event.eventType(),e);}
  Timestamp occurred=Timestamp.from(event.occurredAt());
  try(PreparedStatement s=c.prepareStatement(INSERT)){
   s.setObject(1,UUID.randomUUID());s.setObject(2,event.eventId());s.setObject(3,tenant.value());s.setString(4,event.eventType());
   s.setString(5,json);s.setTimestamp(6,occurred);s.setTimestamp(7,occurred);s.executeUpdate();
  }
 }
}
