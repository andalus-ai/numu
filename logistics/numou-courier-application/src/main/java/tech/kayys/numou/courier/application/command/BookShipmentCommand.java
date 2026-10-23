package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Input for booking a shipment with one or more independently identifiable parcels. */
public record BookShipmentCommand(
        TenantId tenantId, ShipmentId shipmentId, String reference,
        ShipmentAddress sender, ShipmentAddress recipient, ServiceLevel serviceLevel,
        List<Parcel> parcels, Instant occurredAt) {
    public BookShipmentCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(recipient, "recipient must not be null");
        Objects.requireNonNull(serviceLevel, "serviceLevel must not be null");
        Objects.requireNonNull(parcels, "parcels must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference must not be blank");
        if (parcels.isEmpty()) throw new IllegalArgumentException("at least one parcel is required");
        parcels = List.copyOf(parcels);
    }
}