package tech.kayys.numou.courier.domain;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Immutable, versioned tenant-owned tariff. Publish a new version instead of mutating a quote's source. */
public record CourierRateCard(UUID id, TenantId tenantId, String version, ServiceLevel serviceLevel,
 String originZone, String destinationZone, Currency currency, BigDecimal baseFee,
 BigDecimal perChargeableKg, BigDecimal dimensionalDivisorCm3PerKg, BigDecimal taxRate,
 Instant effectiveFrom, Instant effectiveUntil) {
 public CourierRateCard {
  Objects.requireNonNull(id); Objects.requireNonNull(tenantId); version=required(version,"version");
  Objects.requireNonNull(serviceLevel); originZone=zone(originZone,"originZone"); destinationZone=zone(destinationZone,"destinationZone");
  Objects.requireNonNull(currency); baseFee=nonNegative(baseFee,"baseFee"); perChargeableKg=nonNegative(perChargeableKg,"perChargeableKg");
  dimensionalDivisorCm3PerKg=positive(dimensionalDivisorCm3PerKg,"dimensionalDivisorCm3PerKg"); taxRate=nonNegative(taxRate,"taxRate");
  if(taxRate.compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("taxRate must be between 0 and 1");
  Objects.requireNonNull(effectiveFrom); if(effectiveUntil!=null&&!effectiveUntil.isAfter(effectiveFrom)) throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
 }
 public boolean effectiveAt(Instant instant){Objects.requireNonNull(instant);return !instant.isBefore(effectiveFrom)&&(effectiveUntil==null||instant.isBefore(effectiveUntil));}
 private static String required(String s,String f){Objects.requireNonNull(s,f);String v=s.trim();if(v.isEmpty())throw new IllegalArgumentException(f+" must not be blank");return v;}
 private static String zone(String s,String f){return required(s,f).toUpperCase(java.util.Locale.ROOT);}
 private static BigDecimal nonNegative(BigDecimal v,String f){Objects.requireNonNull(v,f);if(v.signum()<0)throw new IllegalArgumentException(f+" must be non-negative");return v;}
 private static BigDecimal positive(BigDecimal v,String f){Objects.requireNonNull(v,f);if(v.signum()<=0)throw new IllegalArgumentException(f+" must be positive");return v;}
}