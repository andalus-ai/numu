package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CourierIntegrationEndpointTest {
 private final Instant now=Instant.parse("2026-10-24T00:00:00Z");
 @Test void requiresHttpsAndOnlyAcceptsConfiguredActiveEvents(){
  CourierIntegrationEndpoint endpoint=new CourierIntegrationEndpoint(UUID.randomUUID(),TenantId.generate(),"partner-a",
   URI.create("https://hooks.example.test/courier"),"vault://tenant-a/courier-hook",Set.of("SHIPMENT_DELIVERED"),
   CourierIntegrationEndpoint.SignatureMode.HMAC_SHA256,CourierIntegrationEndpoint.Status.ACTIVE,now,now);
  assertTrue(endpoint.accepts("SHIPMENT_DELIVERED"));assertFalse(endpoint.accepts("SHIPMENT_BOOKED"));
  assertThrows(IllegalArgumentException.class,()->new CourierIntegrationEndpoint(UUID.randomUUID(),TenantId.generate(),"bad",
   URI.create("http://example.test/hook"),"vault://secret",Set.of("EVENT"),CourierIntegrationEndpoint.SignatureMode.HMAC_SHA256,
   CourierIntegrationEndpoint.Status.ACTIVE,now,now));
 }
}