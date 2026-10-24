package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.domain.ShipmentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/** ETA request carrying actor context for authorization at the application boundary. */
public record EstimateShipmentEtaCommand(TenantId tenantId, ShipmentId shipmentId,
                                         String actorReference, Instant calculatedAt) {
    public EstimateShipmentEtaCommand {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(shipmentId); Objects.requireNonNull(calculatedAt);
        Objects.requireNonNull(actorReference);
        actorReference = actorReference.trim();
        if (actorReference.isEmpty()) throw new IllegalArgumentException("actorReference must not be blank");
    }
}
