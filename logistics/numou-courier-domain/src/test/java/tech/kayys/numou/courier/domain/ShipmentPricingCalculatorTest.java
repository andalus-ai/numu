package tech.kayys.numou.courier.domain;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ShipmentPricingCalculatorTest {
 private static final Instant NOW=Instant.parse("2026-10-24T00:00:00Z");
 @Test void pricesByMaximumOfActualAndDimensionalWeight(){
  TenantId tenant=TenantId.generate();
  Shipment s=Shipment.draft(ShipmentId.generate(),tenant,"NUM-PRICE-1",new ShipmentAddress("Sender","+62111","A",null,"Bandung",null,null,"ID"),
   new ShipmentAddress("Recipient","+62222","B",null,"Jakarta",null,null,"ID"),ServiceLevel.STANDARD,NOW);
  s.addParcel(new Parcel(ParcelId.generate(),"PIECE-01",new BigDecimal("2.500"),new BigDecimal("40"),new BigDecimal("30"),new BigDecimal("20"),null),NOW);
  CourierRateCard card=new CourierRateCard(UUID.randomUUID(),tenant,"ID-STD-2026-10",ServiceLevel.STANDARD,"BDG","JKT",Currency.getInstance("IDR"),
   new BigDecimal("10000"),new BigDecimal("2000"),new BigDecimal("6000"),new BigDecimal("0.11"),NOW,null);
  ShipmentQuote q=ShipmentPricingCalculator.quote(s,card,"BDG","JKT",NOW,null);
  assertEquals(new BigDecimal("2.500"),q.actualWeightKg());assertEquals(new BigDecimal("4.000"),q.dimensionalWeightKg());
  assertEquals(new BigDecimal("4.000"),q.chargeableWeightKg());assertEquals(new BigDecimal("8000.00"),q.weightFee());
  assertEquals(new BigDecimal("1980.00"),q.tax());assertEquals(new BigDecimal("19980.00"),q.total());
 }
 @Test void rejectsForeignTenantAndFutureTariff(){
  TenantId tenant=TenantId.generate();Shipment s=Shipment.draft(ShipmentId.generate(),tenant,"NUM-PRICE-2",
   new ShipmentAddress("Sender","+62111","A",null,"Bandung",null,null,"ID"),new ShipmentAddress("Recipient","+62222","B",null,"Jakarta",null,null,"ID"),ServiceLevel.STANDARD,NOW);
  s.addParcel(new Parcel(ParcelId.generate(),"PIECE-02",BigDecimal.ONE,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,null),NOW);
  CourierRateCard future=new CourierRateCard(UUID.randomUUID(),tenant,"FUTURE",ServiceLevel.STANDARD,"BDG","JKT",Currency.getInstance("IDR"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("6000"),BigDecimal.ZERO,NOW.plusSeconds(60),null);
  assertThrows(IllegalArgumentException.class,()->ShipmentPricingCalculator.quote(s,future,"BDG","JKT",NOW,null));
  CourierRateCard foreign=new CourierRateCard(UUID.randomUUID(),TenantId.generate(),"FOREIGN",ServiceLevel.STANDARD,"BDG","JKT",Currency.getInstance("IDR"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("6000"),BigDecimal.ZERO,NOW,null);
  assertThrows(SecurityException.class,()->ShipmentPricingCalculator.quote(s,foreign,"BDG","JKT",NOW,null));
 }
}