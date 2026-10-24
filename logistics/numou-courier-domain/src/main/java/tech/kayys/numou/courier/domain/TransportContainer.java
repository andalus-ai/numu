package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-owned physical container that groups parcels for handling and custody.
 * References are opaque business identifiers; no carrier-specific API types leak into the domain.
 */
public final class TransportContainer {
    private final UUID id;
    private final TenantId tenantId;
    private final String reference;
    private final TransportContainerType type;
    private final String originHubReference;
    private final String destinationHubReference;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant sealedAt;
    private Instant dispatchedAt;
    private Instant receivedAt;
    private TransportContainerStatus status = TransportContainerStatus.OPEN;
    private String sealReference;
    private String sealedBy;
    private String dispatchedWithManifestReference;
    private String receivedBy;
    private final Set<String> parcelReferences = new LinkedHashSet<>();

    private TransportContainer(UUID id, TenantId tenantId, String reference,
                               TransportContainerType type, String originHubReference,
                               String destinationHubReference, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.reference = required(reference, 80, "reference");
        this.type = Objects.requireNonNull(type, "type");
        this.originHubReference = required(originHubReference, 160, "originHubReference");
        this.destinationHubReference = required(destinationHubReference, 160, "destinationHubReference");
        if (this.originHubReference.equals(this.destinationHubReference)) {
            throw new IllegalArgumentException("origin and destination hubs must differ");
        }
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public static TransportContainer create(UUID id, TenantId tenantId, String reference,
                                            TransportContainerType type, String originHubReference,
                                            String destinationHubReference, Instant now) {
        return new TransportContainer(id, tenantId, reference, type, originHubReference, destinationHubReference, now);
    }

    public void addParcel(String parcelReference, Instant now) {
        requireStatus(TransportContainerStatus.OPEN);
        String parcel = required(parcelReference, 160, "parcelReference");
        if (!parcelReferences.add(parcel)) throw new IllegalArgumentException("parcel already belongs to this container");
        touch(now);
    }

    public void removeParcel(String parcelReference, Instant now) {
        requireStatus(TransportContainerStatus.OPEN);
        String parcel = required(parcelReference, 160, "parcelReference");
        if (!parcelReferences.remove(parcel)) throw new IllegalArgumentException("parcel is not in this container");
        touch(now);
    }

    public void seal(String sealReference, String actorReference, Instant now) {
        requireStatus(TransportContainerStatus.OPEN);
        if (parcelReferences.isEmpty()) throw new IllegalStateException("empty container cannot be sealed");
        this.sealReference = required(sealReference, 160, "sealReference");
        this.sealedBy = required(actorReference, 160, "actorReference");
        this.sealedAt = monotonic(now);
        this.updatedAt = sealedAt;
        this.status = TransportContainerStatus.SEALED;
    }

    public void dispatch(String manifestReference, Instant now) {
        requireStatus(TransportContainerStatus.SEALED);
        this.dispatchedWithManifestReference = required(manifestReference, 160, "manifestReference");
        this.dispatchedAt = monotonic(now);
        this.updatedAt = dispatchedAt;
        this.status = TransportContainerStatus.DISPATCHED;
    }

    /** Records receiving custody; seal verification outcome must be captured by the calling workflow. */
    public void receive(String actorReference, String observedSealReference, Instant now) {
        requireStatus(TransportContainerStatus.DISPATCHED);
        String actor = required(actorReference, 160, "actorReference");
        String observed = required(observedSealReference, 160, "observedSealReference");
        if (!sealReference.equals(observed)) {
            throw new IllegalStateException("seal mismatch; route through the seal-exception workflow");
        }
        this.receivedBy = actor;
        this.receivedAt = monotonic(now);
        this.updatedAt = receivedAt;
        this.status = TransportContainerStatus.RECEIVED;
    }

    public void cancel(Instant now) {
        if (status == TransportContainerStatus.CANCELLED) return;
        if (status != TransportContainerStatus.OPEN) {
            throw new IllegalStateException("only an open container can be cancelled");
        }
        touch(now);
        status = TransportContainerStatus.CANCELLED;
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String reference() { return reference; }
    public TransportContainerType type() { return type; }
    public String originHubReference() { return originHubReference; }
    public String destinationHubReference() { return destinationHubReference; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant sealedAt() { return sealedAt; }
    public Instant dispatchedAt() { return dispatchedAt; }
    public Instant receivedAt() { return receivedAt; }
    public TransportContainerStatus status() { return status; }
    public String sealReference() { return sealReference; }
    public String sealedBy() { return sealedBy; }
    public String dispatchedWithManifestReference() { return dispatchedWithManifestReference; }
    public String receivedBy() { return receivedBy; }
    public Set<String> parcelReferences() { return Collections.unmodifiableSet(parcelReferences); }

    private void requireStatus(TransportContainerStatus expected) {
        if (status != expected) throw new IllegalStateException("expected " + expected + " but was " + status);
    }
    private void touch(Instant now) { updatedAt = monotonic(now); }
    private Instant monotonic(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("time must not move backwards");
        return now;
    }
    private static String required(String value, int max, String field) {
        Objects.requireNonNull(value, field);
        String result = value.trim();
        if (result.isEmpty() || result.length() > max) {
            throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
        }
        return result;
    }
}
