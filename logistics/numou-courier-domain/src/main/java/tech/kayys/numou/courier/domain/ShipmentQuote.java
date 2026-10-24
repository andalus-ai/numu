package tech.kayys.numou.courier.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Immutable pricing snapshot. Existing quotes are never recalculated in place. */
public record ShipmentQuote(UUID id, UUID rateCardId, String rateCardVersion, ShipmentId shipmentId,
 Currency currency, BigDecimal actualWeightKg, BigDecimal dimensionalWeightKg, BigDecimal chargeableWeightKg,
 BigDecimal baseFee, BigDecimal weightFee, BigDecimal tax, BigDecimal total, Instant quotedAt, Instant expiresAt) {
 public ShipmentQuote {
  Objects.requireNonNull(id);Objects.requireNonNull(rateCardId);Objects.requireNonNull(rateCardVersion);Objects.requireNonNull(shipmentId);
  Objects.requireNonNull(currency);Objects.requireNonNull(quotedAt);
  actualWeightKg=nonNegative(actualWeightKg,"actualWeightKg");dimensionalWeightKg=nonNegative(dimensionalWeightKg,"dimensionalWeightKg");
  chargeableWeightKg=nonNegative(chargeableWeightKg,"chargeableWeightKg");baseFee=nonNegative(baseFee,"baseFee");
  weightFee=nonNegative(weightFee,"weightFee");tax=nonNegative(tax,"tax");total=nonNegative(total,"total");
  if(expiresAt!=null&&!expiresAt.isAfter(quotedAt))throw new IllegalArgumentException("expiresAt must be after quotedAt");
  if(chargeableWeightKg.compareTo(actualWeightKg.max(dimensionalWeightKg))!=0)throw new IllegalArgumentException("chargeable weight must equal max(actual, dimensional)");
  if(total.compareTo(baseFee.add(weightFee).add(tax))!=0)throw new IllegalArgumentException("total must equal base fee + weight fee + tax");
 }
 private static BigDecimal nonNegative(BigDecimal v,String f){Objects.requireNonNull(v,f);if(v.signum()<0)throw new IllegalArgumentException(f+" must be non-negative");return v;}
}