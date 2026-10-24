package tech.kayys.numou.courier.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Deterministic quote calculator using parcel dimensions in centimetres. */
public final class ShipmentPricingCalculator {
 private static final int MONEY_SCALE=2;
 private ShipmentPricingCalculator(){}
 public static ShipmentQuote quote(Shipment shipment,CourierRateCard card,String originZone,String destinationZone,Instant quotedAt,Instant expiresAt){
  Objects.requireNonNull(shipment);Objects.requireNonNull(card);Objects.requireNonNull(quotedAt);
  if(!shipment.tenantId().equals(card.tenantId()))throw new SecurityException("rate card belongs to another tenant");
  if(shipment.serviceLevel()!=card.serviceLevel())throw new IllegalArgumentException("service level does not match rate card");
  if(!card.originZone().equalsIgnoreCase(Objects.requireNonNull(originZone))||!card.destinationZone().equalsIgnoreCase(Objects.requireNonNull(destinationZone)))throw new IllegalArgumentException("rate card zones do not match shipment route");
  if(!card.effectiveAt(quotedAt))throw new IllegalArgumentException("rate card is not effective at quote time");
  if(shipment.parcels().isEmpty())throw new IllegalStateException("shipment must contain parcels before pricing");
  BigDecimal actual=BigDecimal.ZERO,dimensional=BigDecimal.ZERO;
  for(Parcel p:shipment.parcels()){
   actual=actual.add(p.weightKg());
   BigDecimal volume=p.lengthCm().multiply(p.widthCm()).multiply(p.heightCm());
   dimensional=dimensional.add(volume.divide(card.dimensionalDivisorCm3PerKg(),6,RoundingMode.HALF_UP));
  }
  actual=actual.setScale(3,RoundingMode.HALF_UP);dimensional=dimensional.setScale(3,RoundingMode.HALF_UP);
  BigDecimal chargeable=actual.max(dimensional),base=money(card.baseFee()),weightFee=money(card.perChargeableKg().multiply(chargeable));
  BigDecimal tax=money(base.add(weightFee).multiply(card.taxRate()));
  return new ShipmentQuote(UUID.randomUUID(),card.id(),card.version(),shipment.id(),card.currency(),actual,dimensional,chargeable,base,weightFee,tax,
   base.add(weightFee).add(tax).setScale(MONEY_SCALE,RoundingMode.HALF_UP),quotedAt,expiresAt);
 }
 private static BigDecimal money(BigDecimal v){return v.setScale(MONEY_SCALE,RoundingMode.HALF_UP);}
}