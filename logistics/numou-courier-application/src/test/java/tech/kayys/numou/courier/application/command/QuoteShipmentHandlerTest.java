package tech.kayys.numou.courier.application.command;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuoteShipmentHandlerTest {
 @Test void snapshotsTheEffectiveTenantTariffForShipment(){
  Instant now=Instant.parse("2026-10-24T00:00:00Z");TenantId tenant=TenantId.generate();
  Shipment shipment=Shipment.draft(ShipmentId.generate(),tenant,"NUM-QUOTE-1",
   new ShipmentAddress("Sender","+62111","A",null,"Bandung",null,null,"ID"),
   new ShipmentAddress("Recipient","+62222","B",null,"Jakarta",null,null,"ID"),ServiceLevel.STANDARD,now);
  shipment.addParcel(new Parcel(ParcelId.generate(),"PIECE-01",new BigDecimal("1"),new BigDecimal("10"),new BigDecimal("10"),new BigDecimal("10"),null),now);
  CourierRateCard card=new CourierRateCard(UUID.randomUUID(),tenant,"ID-STD-1",ServiceLevel.STANDARD,"BDG","JKT",
   Currency.getInstance("IDR"),new BigDecimal("10000"),new BigDecimal("1000"),new BigDecimal("6000"),new BigDecimal("0.11"),now,null);
  ShipmentRepository shipments=mock(ShipmentRepository.class);CourierCommercialRepository commercial=mock(CourierCommercialRepository.class);
  when(shipments.findById(tenant,shipment.id())).thenReturn(CompletableFuture.completedFuture(Optional.of(shipment)));
  when(commercial.findEffectiveRateCard(tenant,ServiceLevel.STANDARD,"BDG","JKT",now)).thenReturn(CompletableFuture.completedFuture(Optional.of(card)));
  when(commercial.saveQuote(eq(tenant),any(ShipmentQuote.class))).thenAnswer(inv->CompletableFuture.completedFuture(inv.getArgument(1)));
  ShipmentQuote quote=new QuoteShipmentHandler(shipments,commercial).handle(new QuoteShipmentCommand(tenant,shipment.id(),"bdg","jkt",now,null)).toCompletableFuture().join();
  assertEquals(card.id(),quote.rateCardId());assertEquals("ID-STD-1",quote.rateCardVersion());assertEquals(new BigDecimal("10000.00"),quote.baseFee());
  verify(commercial).saveQuote(eq(tenant),any(ShipmentQuote.class));
 }
 @Test void refusesToQuoteShipmentNotOwnedOrVisibleToTenant(){
  TenantId tenant=TenantId.generate();ShipmentRepository shipments=mock(ShipmentRepository.class);CourierCommercialRepository commercial=mock(CourierCommercialRepository.class);
  when(shipments.findById(eq(tenant),any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
  assertThrows(java.util.concurrent.CompletionException.class,()->new QuoteShipmentHandler(shipments,commercial)
   .handle(new QuoteShipmentCommand(tenant,ShipmentId.generate(),"BDG","JKT",Instant.parse("2026-10-24T00:00:00Z"),null)).toCompletableFuture().join());
  verifyNoInteractions(commercial);
 }
}