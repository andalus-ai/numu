package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CourierPartnerAgreementTest {
 @Test void agreementUsableOnlyWhenActiveAndInsideEffectiveWindow(){
  Instant start=Instant.parse("2026-10-24T00:00:00Z");
  CourierPartnerAgreement agreement=new CourierPartnerAgreement(UUID.randomUUID(),TenantId.generate(),UUID.randomUUID(),"v1",
   ServiceLevel.STANDARD,"BDG","JKT",start,start.plusSeconds(3600),CourierPartnerAgreement.Status.ACTIVE);
  assertTrue(agreement.usableAt(start));assertTrue(agreement.usableAt(start.plusSeconds(3599)));
  assertFalse(agreement.usableAt(start.plusSeconds(3600)));
 }
 @Test void rejectsInvalidEffectiveWindow(){
  Instant start=Instant.parse("2026-10-24T00:00:00Z");
  assertThrows(IllegalArgumentException.class,()->new CourierPartnerAgreement(UUID.randomUUID(),TenantId.generate(),UUID.randomUUID(),
   "v1",ServiceLevel.STANDARD,"BDG","JKT",start,start,CourierPartnerAgreement.Status.ACTIVE));
 }
}