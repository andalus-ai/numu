package tech.kayys.numou.courier.application.command;
import tech.kayys.numou.courier.domain.CourierCommercialRepository;
import tech.kayys.numou.courier.domain.ShipmentPricingCalculator;
import tech.kayys.numou.courier.domain.ShipmentQuote;
import tech.kayys.numou.courier.domain.ShipmentRepository;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Resolves a tenant-scoped shipment and tariff before persisting a deterministic quote snapshot. */
public final class QuoteShipmentHandler {
 private final ShipmentRepository shipments;private final CourierCommercialRepository commercial;
 public QuoteShipmentHandler(ShipmentRepository shipments,CourierCommercialRepository commercial){
  this.shipments=Objects.requireNonNull(shipments);this.commercial=Objects.requireNonNull(commercial);
 }
 public CompletionStage<ShipmentQuote> handle(QuoteShipmentCommand command){
  Objects.requireNonNull(command);
  return shipments.findById(command.tenantId(),command.shipmentId()).thenCompose(found->{
   if(found.isEmpty())return CompletableFuture.failedFuture(new IllegalArgumentException("shipment not found for tenant"));
   var shipment=found.get();
   return commercial.findEffectiveRateCard(command.tenantId(),shipment.serviceLevel(),command.originZone(),command.destinationZone(),command.quotedAt())
    .thenCompose(rate->{
     if(rate.isEmpty())return CompletableFuture.failedFuture(new IllegalStateException("no effective rate card for shipment route and service"));
     ShipmentQuote quote=ShipmentPricingCalculator.quote(shipment,rate.get(),command.originZone(),command.destinationZone(),command.quotedAt(),command.expiresAt());
     return commercial.saveQuote(command.tenantId(),quote);
    });
  });
 }
}