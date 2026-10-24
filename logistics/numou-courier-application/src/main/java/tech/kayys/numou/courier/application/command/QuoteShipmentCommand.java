package tech.kayys.numou.courier.application.command;
import tech.kayys.numou.courier.domain.ShipmentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/** Explicit quote request; route zones must come from trusted routing/branch data, not client authorization claims. */
public record QuoteShipmentCommand(TenantId tenantId, ShipmentId shipmentId, String originZone,
 String destinationZone, Instant quotedAt, Instant expiresAt) {
 public QuoteShipmentCommand {
  Objects.requireNonNull(tenantId);Objects.requireNonNull(shipmentId);Objects.requireNonNull(quotedAt);
  originZone=required(originZone,"originZone");destinationZone=required(destinationZone,"destinationZone");
  if(expiresAt!=null&&!expiresAt.isAfter(quotedAt))throw new IllegalArgumentException("expiresAt must be after quotedAt");
 }
 private static String required(String v,String field){Objects.requireNonNull(v,field);String s=v.trim().toUpperCase(java.util.Locale.ROOT);if(s.isEmpty())throw new IllegalArgumentException(field+" must not be blank");return s;}
}