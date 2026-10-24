package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CodObligationTest {
 private final Instant now=Instant.parse("2026-10-24T00:00:00Z");
 private CodObligation obligation(){return new CodObligation(UUID.randomUUID(),TenantId.generate(),ShipmentId.generate(),
  new BigDecimal("250000"),Currency.getInstance("IDR"),now);}
 @Test void collectionAndSettlementRecordVarianceExplicitly(){
  CodObligation cod=obligation();
  cod.collect("courier-9","BDG-01",new BigDecimal("250000"),now.plusSeconds(60));
  assertEquals(CodObligationStatus.COLLECTED,cod.status());
  cod.settle("closeout-2026-10-24-01",new BigDecimal("249000"),now.plusSeconds(120));
  assertEquals(CodObligationStatus.VARIANCE_RECORDED,cod.status());
  assertEquals(new BigDecimal("1000"),cod.variance());
 }
 @Test void preventsSettlementBeforeCollectionAndDuplicateCollection(){
  CodObligation cod=obligation();
  assertThrows(IllegalStateException.class,()->cod.settle("closeout",BigDecimal.ZERO,now.plusSeconds(2)));
  cod.collect("courier-9","BDG-01",new BigDecimal("250000"),now.plusSeconds(1));
  assertThrows(IllegalStateException.class,()->cod.collect("courier-9","BDG-01",new BigDecimal("250000"),now.plusSeconds(2)));
 }
}