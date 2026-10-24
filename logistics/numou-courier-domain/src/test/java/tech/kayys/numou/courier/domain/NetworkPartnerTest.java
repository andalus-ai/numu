package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class NetworkPartnerTest {
 @Test void normalizesCodesAndOnlyActivePartnersAcceptSupportedServices(){
  Instant now=Instant.parse("2026-10-24T00:00:00Z");
  NetworkPartner pending=new NetworkPartner(UUID.randomUUID(),TenantId.generate()," idn-post ","Indonesian Post",
   NetworkPartnerStatus.PENDING,Set.of("same_day","standard"),now,now);
  assertEquals("IDN-POST",pending.partnerCode());
  assertFalse(pending.canAccept("STANDARD"));
  NetworkPartner active=new NetworkPartner(pending.id(),pending.tenantId(),pending.partnerCode(),pending.displayName(),
   NetworkPartnerStatus.ACTIVE,pending.supportedServices(),now,now);
  assertTrue(active.canAccept("standard"));assertFalse(active.canAccept("international"));
 }
 @Test void rejectsEmptyCapabilitiesAndInvalidTimestamps(){
  Instant now=Instant.parse("2026-10-24T00:00:00Z");
  assertThrows(IllegalArgumentException.class,()->new NetworkPartner(UUID.randomUUID(),TenantId.generate(),"P","Partner",
   NetworkPartnerStatus.ACTIVE,Set.of(),now,now));
  assertThrows(IllegalArgumentException.class,()->new NetworkPartner(UUID.randomUUID(),TenantId.generate(),"P","Partner",
   NetworkPartnerStatus.ACTIVE,Set.of("standard"),now,now.minusSeconds(1)));
 }
}