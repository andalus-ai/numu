package tech.kayys.syirkah.asset.domain.model;

import tech.kayys.syirkah.asset.domain.classification.AssetClassification;
import tech.kayys.syirkah.asset.domain.custody.AssetCustody;
import tech.kayys.syirkah.asset.domain.event.AssetActivated;
import tech.kayys.syirkah.asset.domain.event.AssetAssigned;
import tech.kayys.syirkah.asset.domain.event.AssetClassificationCleared;
import tech.kayys.syirkah.asset.domain.event.AssetClassified;
import tech.kayys.syirkah.asset.domain.event.AssetCreated;
import tech.kayys.syirkah.asset.domain.event.AssetDisposed;
import tech.kayys.syirkah.asset.domain.event.AssetLocationChanged;
import tech.kayys.syirkah.asset.domain.event.AssetLocationCleared;
import tech.kayys.syirkah.asset.domain.event.AssetRetired;
import tech.kayys.syirkah.asset.domain.event.AssetSuspended;
import tech.kayys.syirkah.asset.domain.event.AssetUnassigned;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;
import tech.kayys.syirkah.asset.domain.location.AssetLocation;
import tech.kayys.syirkah.asset.domain.valueobject.AssetStatus;
import tech.kayys.syirkah.asset.domain.valueobject.AssetType;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.util.Objects;
import java.util.UUID;

/**
 * Asset aggregate root.
 *
 * <p>The Asset is a business asset, not merely a database record: it owns its
 * identity, lifecycle, current location and current custody, while heavier
 * capabilities live in separate bounded contexts.</p>
 */
public final class Asset extends AbstractAggregateRoot<AssetId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String assetNumber;
    private String name;
    private AssetType type;
    private AssetStatus status;

    private AssetLocation location;
    private AssetCustody custody;
    private AssetClassification classification;

    private Asset() {
        super();
    }

    private Asset(AssetId id, String tenantId, String assetNumber, String name, AssetType type) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.assetNumber = requireText(assetNumber, "assetNumber");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.status = AssetStatus.DRAFT;
    }

    public static Asset create(AssetId id, String tenantId, String assetNumber, String name, AssetType type, DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        Asset asset = new Asset(id, tenantId, assetNumber, name, type);
        asset.setCreatedAt(clock.now());
        asset.setUpdatedAt(clock.now());
        asset.raise(new AssetCreated(UUID.randomUUID(), clock.now(), id.value(), asset.assetNumber));
        return asset;
    }

    public static Asset reconstitute(AssetId id, String tenantId, String assetNumber, String name, AssetType type,
                                     AssetStatus status, AssetLocation location, AssetCustody custody, AssetClassification classification) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Asset asset = new Asset(id, tenantId, assetNumber, name, type);
        asset.status = status;
        asset.location = location;
        asset.custody = custody;
        asset.classification = classification;
        return asset;
    }

    public void activate(DomainClock clock) {
        requireStatus(AssetStatus.DRAFT);
        this.status = AssetStatus.ACTIVE;
        touch(clock);
        raise(new AssetActivated(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void suspend(DomainClock clock) {
        requireStatus(AssetStatus.ACTIVE);
        this.status = AssetStatus.SUSPENDED;
        touch(clock);
        raise(new AssetSuspended(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void retire(DomainClock clock) {
        requireStatus(AssetStatus.ACTIVE, AssetStatus.SUSPENDED);
        this.status = AssetStatus.RETIRED;
        touch(clock);
        raise(new AssetRetired(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void dispose(DomainClock clock) {
        requireStatus(AssetStatus.RETIRED);
        this.status = AssetStatus.DISPOSED;
        touch(clock);
        raise(new AssetDisposed(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void rename(String newName, DomainClock clock) {
        this.name = requireText(newName, "name");
        touch(clock);
    }

    public void changeType(AssetType newType, DomainClock clock) {
        Objects.requireNonNull(newType, "newType cannot be null");
        if (status == AssetStatus.DISPOSED) {
            throw new InvalidStateException("Disposed asset cannot change type");
        }
        this.type = newType;
        touch(clock);
    }

    public void changeLocation(AssetLocation newLocation, DomainClock clock) {
        Objects.requireNonNull(newLocation, "newLocation cannot be null");
        requireNotDisposed("change location");
        this.location = newLocation;
        touch(clock);
        raise(new AssetLocationChanged(UUID.randomUUID(), clock.now(), id.value(), newLocation.locationId(), newLocation.name()));
    }

    public void clearLocation(DomainClock clock) {
        requireNotDisposed("clear location");
        this.location = null;
        touch(clock);
        raise(new AssetLocationCleared(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void assign(AssetCustody newCustody, DomainClock clock) {
        Objects.requireNonNull(newCustody, "newCustody cannot be null");
        requireNotDisposed("assign custody");
        this.custody = newCustody;
        touch(clock);
        raise(new AssetAssigned(UUID.randomUUID(), clock.now(), id.value(), newCustody.partyId(), newCustody.partyType(), newCustody.partyName()));
    }

    public void unassign(DomainClock clock) {
        requireNotDisposed("unassign custody");
        this.custody = null;
        touch(clock);
        raise(new AssetUnassigned(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void classify(AssetClassification newClassification, DomainClock clock) {
        Objects.requireNonNull(newClassification, "newClassification cannot be null");
        requireNotDisposed("classify");
        this.classification = newClassification;
        touch(clock);
        raise(new AssetClassified(UUID.randomUUID(), clock.now(), id.value(), newClassification.classificationId(), newClassification.name()));
    }

    public void clearClassification(DomainClock clock) {
        requireNotDisposed("clear classification");
        this.classification = null;
        touch(clock);
        raise(new AssetClassificationCleared(UUID.randomUUID(), clock.now(), id.value()));
    }

    private void requireStatus(AssetStatus... allowed) {
        for (AssetStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new InvalidStateException("Asset cannot transition from " + status);
    }

    private void requireNotDisposed(String operation) {
        if (status == AssetStatus.DISPOSED) {
            throw new InvalidStateException("Disposed asset cannot " + operation);
        }
    }

    private void touch(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessRuleViolation(field + " cannot be blank");
        }
        return normalized;
    }

    public String tenantId() { return tenantId; }
    public String assetNumber() { return assetNumber; }
    public String name() { return name; }
    public AssetType type() { return type; }
    public AssetStatus status() { return status; }
    public AssetLocation location() { return location; }
    public AssetCustody custody() { return custody; }
    public AssetClassification classification() { return classification; }
    public boolean isAssigned() { return custody != null; }
    public boolean hasLocation() { return location != null; }
}
