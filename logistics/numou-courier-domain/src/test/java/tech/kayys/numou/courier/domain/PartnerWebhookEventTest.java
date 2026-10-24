package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PartnerWebhookEventTest {
 @Test void requiresStablePartnerEventKeyAndJsonObjectPayload(){
  Instant now=Instant.parse("2026-10-24T00:00:00Z");TenantId tenant=TenantId.generate();
  assertThrows(IllegalArgumentException.class,()->new PartnerWebhookEvent(UUID.randomUUID(),tenant,UUID.randomUUID()," ","TRACKING","{}",now,now));
  assertThrows(IllegalArgumentException.class,()->new PartnerWebhookEvent(UUID.randomUUID(),tenant,UUID.randomUUID(),"evt-1","TRACKING","[]",now,now));
  assertDoesNotThrow(()->new PartnerWebhookEvent(UUID.randomUUID(),tenant,UUID.randomUUID(),"evt-1","TRACKING","{\"status\":\"IN_TRANSIT\"}",now,now));
 }
}