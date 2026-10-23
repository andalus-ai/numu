package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tenant-owned shipment booking aggregate. Sender/recipient addresses are snapshots;
 * parcels are independently scannable physical pieces.
 */
public final class Shipment {
    private final ShipmentId id;
    private final TenantId tenantId;
    private final String reference;
    private final ShipmentAddress sender;
    private final ShipmentAddress recipient;
    private final ServiceLevel serviceLevel;
    private final Instant createdAt;
    private ShipmentStatus status;
    private final List<Parcel> parcels = new ArrayList<>();
    private Instant updatedAt;
    private Instant bookedAt;

    private Shipment(ShipmentId id, TenantId tenantId, String reference,
                     ShipmentAddress sender, ShipmentAddress recipient,
                     ServiceLevel serviceLevel, Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.reference = required(reference, "reference").toUpperCase(java.util.Locale.ROOT);
        if (!this.reference.matches("[A-Z0-9][A-Z0-9._-]{2,39}"))
            throw new IllegalArgumentException("reference must be 3-40 characters using letters, digits, '.', '_' or '-'");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.recipient = Objects.requireNonNull(recipient, "recipient must not be null");
        this.serviceLevel = Objects.requireNonNull(serviceLevel, "serviceLevel must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        this.status = ShipmentStatus.DRAFT;
    }

    public static Shipment draft(ShipmentId id, TenantId tenantId, String reference,
                                 ShipmentAddress sender, ShipmentAddress recipient,
                                 ServiceLevel serviceLevel, Instant now) {
        return new Shipment(id, tenantId, reference, sender, recipient, serviceLevel, now);
    }

    public void addParcel(Parcel parcel, Instant now) {
        requireDraft();
        Objects.requireNonNull(parcel, "parcel must not be null");
        if (parcels.stream().anyMatch(existing -> existing.id().equals(parcel.id())))
            throw new IllegalArgumentException("parcel id already exists in shipment");
        if (parcels.stream().anyMatch(existing -> existing.reference().equalsIgnoreCase(parcel.reference())))
            throw new IllegalArgumentException("parcel reference already exists in shipment");
        parcels.add(parcel);
        updatedAt = monotonic(now);
    }

    public void book(Instant now) {
        requireDraft();
        if (parcels.isEmpty()) throw new IllegalStateException("a shipment must contain at least one parcel before booking");
        bookedAt = monotonic(now);
        updatedAt = bookedAt;
        status = ShipmentStatus.BOOKED;
    }

    public void cancel(Instant now) {
        if (status == ShipmentStatus.CANCELLED) return;
        if (status != ShipmentStatus.DRAFT && status != ShipmentStatus.BOOKED)
            throw new IllegalStateException("shipment cannot be cancelled from " + status);
        updatedAt = monotonic(now);
        status = ShipmentStatus.CANCELLED;
    }

    private Instant monotonic(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("updated time cannot move backwards");
        return now;
    }
    private void requireDraft() {
        if (status != ShipmentStatus.DRAFT) throw new IllegalStateException("shipment is not a draft");
    }
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }
    public ShipmentId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String reference() { return reference; }
    public ShipmentAddress sender() { return sender; }
    public ShipmentAddress recipient() { return recipient; }
    public ServiceLevel serviceLevel() { return serviceLevel; }
    public ShipmentStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant bookedAt() { return bookedAt; }
    public List<Parcel> parcels() { return List.copyOf(parcels); }
}