package tech.kayys.numou.courier.domain;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit persistence port for courier tariffs, immutable quotes and COD operational facts. */
public interface CourierCommercialRepository {
 CompletionStage<CourierRateCard> saveRateCard(TenantId tenantId, CourierRateCard card);
 CompletionStage<Optional<CourierRateCard>> findEffectiveRateCard(TenantId tenantId, ServiceLevel serviceLevel,
  String originZone, String destinationZone, Instant at);
 CompletionStage<ShipmentQuote> saveQuote(TenantId tenantId, ShipmentQuote quote);
 CompletionStage<CodObligation> saveCodObligation(TenantId tenantId, CodObligation obligation);
}