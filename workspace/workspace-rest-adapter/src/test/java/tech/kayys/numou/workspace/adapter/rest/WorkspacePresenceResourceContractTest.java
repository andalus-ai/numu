package tech.kayys.numou.workspace.adapter.rest;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class WorkspacePresenceResourceContractTest {
 @Test void exposesPageScopedPresenceLeaseEndpoints() throws Exception {
  assertEquals("/api/v1/pages/{pageId}/presence",WorkspacePresenceResource.class.getAnnotation(Path.class).value());
  endpoint("list",GET.class,null);
  endpoint("heartbeat",POST.class,"/{sessionId}/heartbeat");
  endpoint("leave",DELETE.class,"/{sessionId}");
 }
 @Test void presencePayloadDoesNotAcceptTenantOrActorIdentity() {
  var components=Arrays.stream(tech.kayys.numou.workspace.application.WorkspacePresenceService.PresenceSnapshot.class.getRecordComponents()).map(RecordComponent::getName).toList();
  assertEquals(java.util.List.of("pageId","observedAt","users","truncated"),components);
  assertFalse(components.contains("tenantId"));assertFalse(components.contains("actorId"));
 }
 private static void endpoint(String name,Class<?> verb,String path)throws Exception {
  var method=Arrays.stream(WorkspacePresenceResource.class.getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();
  assertNotNull(method.getAnnotation((Class)verb));
  if(path!=null)assertEquals(path,method.getAnnotation(Path.class).value());
 }
}
