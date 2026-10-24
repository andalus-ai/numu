package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Links a local shipment to its identifier at a network partner without replacing the local shipment ID. */
public record PartnerShipmentReference(UUID id, TenantId tenantId, UUID partnerId, ShipmentId shipmentId,
                                       String externalReference, Instant linkedAt) {
    public PartnerShipmentReference {
        Objects.requireNonNull(id);Objects.requireNonNull(tenantId);Objects.requireNonNull(partnerId);Objects.requireNonNull(shipmentId);
        Objects.requireNonNull(linkedAt);externalReference=required(externalReference);
    }
    private static String required(String v){Objects.requireNonNull(v,"externalReference");String s=v.trim();if(s.isEmpty())throw new IllegalArgumentException("externalReference must not be blank");return s;}
}