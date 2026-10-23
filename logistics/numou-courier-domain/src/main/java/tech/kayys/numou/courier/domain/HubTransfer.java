package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Tenant-owned manifest for moving a declared set of shipments between hubs. */
public final class HubTransfer {
    private final String reference;
    private final TenantId tenantId;
    private final HubTransferId id;
    private final HubId originHubId;
    private final HubId destinationHubId;
    private final Instant createdAt;
    private final List<ShipmentId> shipments = new ArrayList<>();
    private HubTransferStatus status = HubTransferStatus.DRAFT;
    private Instant updatedAt;
    private Instant departedAt;
    private Instant arrivedAt;

    private HubTransfer(String reference, TenantId tenantId, HubTransferId id, HubId originHubId,
                        HubId destinationHubId, Instant now) {
        this.reference = required(reference, "reference").toUpperCase(java.util.Locale.ROOT);
        if (!this.reference.matches("[A-Z0-9][A-Z0-9._-]{2,39}"))
            throw new IllegalArgumentException("reference must be 3-40 characters");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.originHubId = Objects.requireNonNull(originHubId, "originHubId must not be null");
        this.destinationHubId = Objects.requireNonNull(destinationHubId, "destinationHubId must not be null");
        if (originHubId.equals(destinationHubId)) throw new IllegalArgumentException("origin and destination hubs must differ");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static HubTransfer draft(String reference, TenantId tenantId, HubTransferId id,
                                    HubId origin, HubId destination, Instant now) {
        return new HubTransfer(reference, tenantId, id, origin, destination, now);
    }

    public void addShipment(ShipmentId shipmentId, Instant now) {
        requireStatus(HubTransferStatus.DRAFT);
        Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        if (shipments.contains(shipmentId)) throw new IllegalArgumentException("shipment already belongs to transfer");
        shipments.add(shipmentId);
        updatedAt = monotonic(now);
    }

    public void seal(Instant now) {
        requireStatus(HubTransferStatus.DRAFT);
        if (shipments.isEmpty()) throw new IllegalStateException("cannot seal an empty transfer");
        status = HubTransferStatus.SEALED;
        updatedAt = monotonic(now);
    }

    public void depart(Instant now) {
        requireStatus(HubTransferStatus.SEALED);
        departedAt = monotonic(now);
        updatedAt = departedAt;
        status = HubTransferStatus.DEPARTED;
    }

    public void arrive(Instant now) {
        requireStatus(HubTransferStatus.DEPARTED);
        arrivedAt = monotonic(now);
        updatedAt = arrivedAt;
        status = HubTransferStatus.ARRIVED;
    }

    public void cancel(Instant now) {
        if (status == HubTransferStatus.CANCELLED) return;
        if (status != HubTransferStatus.DRAFT && status != HubTransferStatus.SEALED)
            throw new IllegalStateException("only draft or sealed transfers can be cancelled");
        status = HubTransferStatus.CANCELLED;
        updatedAt = monotonic(now);
    }

    public static HubTransfer reconstitute(String reference, TenantId tenantId, HubTransferId id,
            HubId origin, HubId destination, HubTransferStatus status, Instant createdAt,
            Instant updatedAt, Instant departedAt, Instant arrivedAt, List<ShipmentId> persistedShipments) {
        HubTransfer transfer = new HubTransfer(reference, tenantId, id, origin, destination, createdAt);
        transfer.status = Objects.requireNonNull(status, "status must not be null");
        transfer.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not be before createdAt");
        if (departedAt != null && (departedAt.isBefore(createdAt) || departedAt.isAfter(updatedAt)))
            throw new IllegalArgumentException("departedAt must be between createdAt and updatedAt");
        if (arrivedAt != null && (departedAt == null || arrivedAt.isBefore(departedAt) || arrivedAt.isAfter(updatedAt)))
            throw new IllegalArgumentException("arrivedAt must be between departedAt and updatedAt");
        if ((status == HubTransferStatus.DEPARTED || status == HubTransferStatus.ARRIVED) && departedAt == null)
            throw new IllegalArgumentException("departed transfer must have departedAt");
        if (status == HubTransferStatus.ARRIVED && arrivedAt == null)
            throw new IllegalArgumentException("arrived transfer must have arrivedAt");
        Set<ShipmentId> unique = new HashSet<>();
        for (ShipmentId shipmentId : Objects.requireNonNull(persistedShipments, "persistedShipments must not be null")) {
            if (!unique.add(Objects.requireNonNull(shipmentId, "shipmentId must not be null")))
                throw new IllegalArgumentException("duplicate shipment in transfer");
            transfer.shipments.add(shipmentId);
        }
        if ((status == HubTransferStatus.SEALED || status == HubTransferStatus.DEPARTED || status == HubTransferStatus.ARRIVED)
                && transfer.shipments.isEmpty()) throw new IllegalArgumentException("sealed transfer must contain shipments");
        transfer.departedAt = departedAt;
        transfer.arrivedAt = arrivedAt;
        return transfer;
    }

    private Instant monotonic(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("time must not move backwards");
        return now;
    }
    private void requireStatus(HubTransferStatus expected) {
        if (status != expected) throw new IllegalStateException("expected " + expected + " but was " + status);
    }
    private static String required(String value, String field) {
        String result = Objects.requireNonNull(value, field + " must not be null").trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }
    public String reference() { return reference; }
    public TenantId tenantId() { return tenantId; }
    public HubTransferId id() { return id; }
    public HubId originHubId() { return originHubId; }
    public HubId destinationHubId() { return destinationHubId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant departedAt() { return departedAt; }
    public Instant arrivedAt() { return arrivedAt; }
    public HubTransferStatus status() { return status; }
    public List<ShipmentId> shipments() { return List.copyOf(shipments); }
}
