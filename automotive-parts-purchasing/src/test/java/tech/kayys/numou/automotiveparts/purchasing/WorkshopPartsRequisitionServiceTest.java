package tech.kayys.numou.automotiveparts.purchasing;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.purchasing.application.RequisitionService;
import tech.kayys.numou.purchasing.domain.procurement.PurchaseRequisition;
import tech.kayys.numou.purchasing.spi.PurchaseRequisitionRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopPartsRequisitionServiceTest {
 @Test void replayIsIdempotentForSameDemand() {
  var service = service(); var demand = demand("demand-1", "2");
  var first = service.requestExternalPart(demand); var replay = service.requestExternalPart(demand);
  assertEquals(first.requisitionId(), replay.requisitionId()); assertEquals(1, replay.lines().size());
  assertEquals("SUBMITTED", replay.status().name());
 }
 @Test void changedQuantityForSameDemandIsRejected() {
  var service = service(); service.requestExternalPart(demand("demand-2", "2"));
  assertThrows(IllegalStateException.class, () -> service.requestExternalPart(demand("demand-2", "3")));
 }
 private static WorkshopPartsRequisitionService service() {
  return new WorkshopPartsRequisitionService(new RequisitionService(new Repo()));
 }
 private static WorkshopPartsRequisitionService.WorkshopPartsDemand demand(String id, String quantity) {
  return new WorkshopPartsRequisitionService.WorkshopPartsDemand("tenant-a", id, "ledger-a", "workshop-parts",
   "WO-100", "WO-100-L1", "SKU-FILTER-01", "fitment-rev-7", new BigDecimal(quantity), "EA",
   Money.of(new BigDecimal("125000"), "IDR"));
 }
 private static final class Repo implements PurchaseRequisitionRepository {
  private final Map<String, PurchaseRequisition> data = new HashMap<>();
  public Optional<PurchaseRequisition> findByTenantAndId(String tenant, String id) { return Optional.ofNullable(data.get(tenant + ":" + id)); }
  public void save(PurchaseRequisition value) { data.put(value.tenantId() + ":" + value.requisitionId(), value); }
 }
}
