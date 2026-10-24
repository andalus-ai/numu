package tech.kayys.numou.courier.domain;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** COD receivable lifecycle. Collection and settlement are explicit operational facts. */
public final class CodObligation {
 private final UUID id;private final TenantId tenantId;private final ShipmentId shipmentId;private final BigDecimal amount;private final Currency currency;private final Instant createdAt;
 private CodObligationStatus status=CodObligationStatus.DUE;private String collectorReference,collectionBranchCode,settlementReference;
 private BigDecimal collectedAmount,settledAmount,variance;private Instant collectedAt,settledAt;
 public CodObligation(UUID id,TenantId tenantId,ShipmentId shipmentId,BigDecimal amount,Currency currency,Instant createdAt){
  this.id=Objects.requireNonNull(id);this.tenantId=Objects.requireNonNull(tenantId);this.shipmentId=Objects.requireNonNull(shipmentId);
  this.amount=positive(amount,"amount");this.currency=Objects.requireNonNull(currency);this.createdAt=Objects.requireNonNull(createdAt);
 }
 public void collect(String collector,String branch,BigDecimal collected,Instant at){
  if(status!=CodObligationStatus.DUE)throw new IllegalStateException("COD can only be collected when due");
  collectorReference=required(collector,"collectorReference");collectionBranchCode=required(branch,"branchCode");
  collectedAmount=nonNegative(collected,"collectedAmount");collectedAt=notBefore(at,createdAt,"collection time");status=CodObligationStatus.COLLECTED;
 }
 public void settle(String reference,BigDecimal settled,Instant at){
  if(status!=CodObligationStatus.COLLECTED)throw new IllegalStateException("COD must be collected before settlement");
  settlementReference=required(reference,"settlementReference");settledAmount=nonNegative(settled,"settledAmount");settledAt=notBefore(at,collectedAt,"settlement time");
  variance=collectedAmount.subtract(settledAmount);status=variance.signum()==0?CodObligationStatus.SETTLED:CodObligationStatus.VARIANCE_RECORDED;
 }
 private static Instant notBefore(Instant v,Instant min,String f){Objects.requireNonNull(v,f);if(v.isBefore(min))throw new IllegalArgumentException(f+" cannot precede prior event");return v;}
 private static BigDecimal positive(BigDecimal v,String f){Objects.requireNonNull(v,f);if(v.signum()<=0)throw new IllegalArgumentException(f+" must be positive");return v;}
 private static BigDecimal nonNegative(BigDecimal v,String f){Objects.requireNonNull(v,f);if(v.signum()<0)throw new IllegalArgumentException(f+" must be non-negative");return v;}
 private static String required(String v,String f){Objects.requireNonNull(v,f);String s=v.trim();if(s.isEmpty())throw new IllegalArgumentException(f+" must not be blank");return s;}
 public UUID id(){return id;}public TenantId tenantId(){return tenantId;}public ShipmentId shipmentId(){return shipmentId;}public BigDecimal amount(){return amount;}
 public Currency currency(){return currency;}public Instant createdAt(){return createdAt;}public CodObligationStatus status(){return status;}
 public String collectorReference(){return collectorReference;}public String collectionBranchCode(){return collectionBranchCode;}public BigDecimal collectedAmount(){return collectedAmount;}
 public Instant collectedAt(){return collectedAt;}public String settlementReference(){return settlementReference;}public BigDecimal settledAmount(){return settledAmount;}
 public BigDecimal variance(){return variance;}public Instant settledAt(){return settledAt;}
}