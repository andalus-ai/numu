package tech.kayys.syirkah.asset.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.asset.domain.event.*;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;
import tech.kayys.syirkah.asset.domain.location.AssetLocation;
import tech.kayys.syirkah.asset.domain.custody.AssetCustody;
import tech.kayys.syirkah.asset.domain.classification.AssetClassification;
import tech.kayys.syirkah.asset.domain.valueobject.AssetStatus;
import tech.kayys.syirkah.asset.domain.valueobject.AssetType;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain tests for the Asset lifecycle and domain events (ASSET-03/04/11/12).
 */
@DisplayName("Asset aggregate")
class AssetTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final DomainClock CLOCK = () -> NOW;

    private static Asset draft() {
        return Asset.create(
                AssetId.generate(), "tenant-1", "AST-001",
                "Delivery Truck", AssetType.VEHICLE, CLOCK);
    }

    private static DomainEvent onlyEvent(Asset asset) {
        List<DomainEvent> events = asset.pullDomainEvents();
        assertEquals(1, events.size());
        return events.get(0);
    }

    @Test
    @DisplayName("is created as DRAFT and raises AssetCreated")
    void createsAssetAsDraft() {
        Asset asset = draft();

        assertEquals(AssetStatus.DRAFT, asset.status());
        assertEquals("tenant-1", asset.tenantId());
        assertEquals("AST-001", asset.assetNumber());

        List<DomainEvent> events = asset.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(AssetCreated.class, events.get(0));
        assertEquals(NOW, events.get(0).occurredAt());
    }

    @Test
    @DisplayName("DRAFT -> ACTIVE raises AssetActivated")
    void activatesAsset() {
        Asset asset = draft();
        asset.pullDomainEvents();

        asset.activate(CLOCK);

        assertEquals(AssetStatus.ACTIVE, asset.status());
        assertInstanceOf(AssetActivated.class, onlyEvent(asset));
    }

    @Test
    @DisplayName("ACTIVE -> SUSPENDED raises AssetSuspended")
    void suspendsAsset() {
        Asset asset = draft();
        asset.pullDomainEvents();
        asset.activate(CLOCK);
        asset.pullDomainEvents();

        asset.suspend(CLOCK);

        assertEquals(AssetStatus.SUSPENDED, asset.status());
        assertInstanceOf(AssetSuspended.class, onlyEvent(asset));
    }

    @Test
    @DisplayName("SUSPENDED -> RETIRED raises AssetRetired")
    void retiresAsset() {
        Asset asset = draft();
        asset.pullDomainEvents();
        asset.activate(CLOCK);
        asset.suspend(CLOCK);
        asset.pullDomainEvents();

        asset.retire(CLOCK);

        assertEquals(AssetStatus.RETIRED, asset.status());
        assertInstanceOf(AssetRetired.class, onlyEvent(asset));
    }

    @Test
    @DisplayName("RETIRED -> DISPOSED raises AssetDisposed")
    void disposesAsset() {
        Asset asset = draft();
        asset.pullDomainEvents();
        asset.activate(CLOCK);
        asset.retire(CLOCK);
        asset.pullDomainEvents();

        asset.dispose(CLOCK);

        assertEquals(AssetStatus.DISPOSED, asset.status());
        assertInstanceOf(AssetDisposed.class, onlyEvent(asset));
    }

    @Test
    @DisplayName("only a DRAFT asset can be activated")
    void rejectsActivateWhenNotDraft() {
        Asset asset = draft();
        asset.activate(CLOCK);

        assertThrows(InvalidStateException.class, () -> asset.activate(CLOCK));
    }

    @Test
    @DisplayName("only a RETIRED asset can be disposed")
    void rejectsDisposeUnlessRetired() {
        Asset asset = draft();
        asset.activate(CLOCK);

        assertThrows(InvalidStateException.class, () -> asset.dispose(CLOCK));
    }

    @Test
    @DisplayName("a DISPOSED asset cannot change type")
    void rejectsMutationAfterDispose() {
        Asset asset = draft();
        asset.activate(CLOCK);
        asset.retire(CLOCK);
        asset.dispose(CLOCK);

        assertThrows(InvalidStateException.class,
                () -> asset.changeType(AssetType.MACHINE, CLOCK));
    }

    @Test
    @DisplayName("reconstitution does not raise any events")
    void reconstitutionDoesNotRaiseEvents() {
        Asset asset = Asset.reconstitute(
                AssetId.generate(), "tenant-1", "AST-002", "Forklift",
                AssetType.EQUIPMENT, AssetStatus.ACTIVE,
                AssetLocation.of("WH-1", "Warehouse 1"),
                AssetCustody.of("EMP-1", "EMPLOYEE", "Aisha"),
                AssetClassification.of("CLS-1", "Material Handling"));

        assertTrue(asset.pullDomainEvents().isEmpty());
        assertEquals(AssetStatus.ACTIVE, asset.status());
        assertTrue(asset.hasLocation());
        assertTrue(asset.isAssigned());
    }

    @Test
    @DisplayName("location, custody and classification changes raise events")
    void raisesLocationCustodyAndClassificationEvents() {
        Asset asset = draft();
        asset.pullDomainEvents();

        asset.changeLocation(AssetLocation.of("WH-2", "Warehouse 2"), CLOCK);
        assertInstanceOf(AssetLocationChanged.class, onlyEvent(asset));

        asset.assign(AssetCustody.of("EMP-7", "EMPLOYEE", "Budi"), CLOCK);
        assertInstanceOf(AssetAssigned.class, onlyEvent(asset));

        asset.classify(AssetClassification.of("CLS-9", "Vehicles"), CLOCK);
        assertInstanceOf(AssetClassified.class, onlyEvent(asset));
    }

    @Test
    @DisplayName("tenantId and assetNumber must not be blank")
    void rejectsBlankIdentity() {
        assertThrows(BusinessRuleViolation.class,
                () -> Asset.create(AssetId.generate(), " ", "AST-9", "N", AssetType.OTHER, CLOCK));
        assertThrows(BusinessRuleViolation.class,
                () -> Asset.create(AssetId.generate(), "tenant-1", "", "N", AssetType.OTHER, CLOCK));
    }

    @Test
    @DisplayName("updatedAt is driven by the injected DomainClock, not the wall clock")
    void usesInjectedClock() {
        Asset asset = draft();
        asset.pullDomainEvents();

        DomainClock later = () -> NOW.plus(Duration.ofDays(1));
        asset.activate(later);

        assertEquals(NOW.plus(Duration.ofDays(1)), asset.getUpdatedAt());
    }
}
