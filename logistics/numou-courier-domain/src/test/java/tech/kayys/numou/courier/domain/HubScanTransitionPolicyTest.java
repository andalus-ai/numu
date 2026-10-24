package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HubScanTransitionPolicyTest {
    private final HubScanTransitionPolicy policy = new HubScanTransitionPolicy();
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final ShipmentId shipment = ShipmentId.of(UUID.randomUUID());
    private final Instant now = Instant.parse("2026-10-24T01:00:00Z");

    @Test
    void sealingRequiresOpenNonEmptyContainerAtOrigin() {
        var open = new HubScanTransitionPolicy.ContainerContext(
                TransportContainerStatus.OPEN, "hub-a", "hub-b", 2);
        assertDoesNotThrow(() -> policy.validate(command(HubScanType.CONTAINER_SEALED, "HUB-A"), open, null));

        var empty = new HubScanTransitionPolicy.ContainerContext(
                TransportContainerStatus.OPEN, "hub-a", "hub-b", 0);
        assertThrows(IllegalStateException.class,
                () -> policy.validate(command(HubScanType.CONTAINER_SEALED, "hub-a"), empty, null));

        var alreadySealed = new HubScanTransitionPolicy.ContainerContext(
                TransportContainerStatus.SEALED, "hub-a", "hub-b", 2);
        assertThrows(IllegalStateException.class,
                () -> policy.validate(command(HubScanType.CONTAINER_SEALED, "hub-a"), alreadySealed, null));
    }

    @Test
    void loadingRequiresExpectedParcelAndLoadingWaveAtOrigin() {
        var loadingWave = new HubScanTransitionPolicy.DispatchWaveContext(
                DispatchWaveStatus.LOADING, "hub-a", "hub-b", true);
        assertDoesNotThrow(() -> policy.validate(command(HubScanType.DISPATCH_LOADED, "hub-a"), null, loadingWave));

        var unexpected = new HubScanTransitionPolicy.DispatchWaveContext(
                DispatchWaveStatus.LOADING, "hub-a", "hub-b", false);
        assertThrows(IllegalStateException.class,
                () -> policy.validate(command(HubScanType.DISPATCH_LOADED, "hub-a"), null, unexpected));

        assertThrows(IllegalStateException.class,
                () -> policy.validate(command(HubScanType.DISPATCH_LOADED, "hub-x"), null, loadingWave));
    }

    @Test
    void containerDispatchRequiresSealedContainerAndMatchingDepartedWave() {
        var sealed = new HubScanTransitionPolicy.ContainerContext(
                TransportContainerStatus.SEALED, "hub-a", "hub-b", 2);
        var departed = new HubScanTransitionPolicy.DispatchWaveContext(
                DispatchWaveStatus.DEPARTED, "hub-a", "hub-b", true);
        assertDoesNotThrow(() -> policy.validate(command(HubScanType.CONTAINER_DISPATCHED, "hub-a"), sealed, departed));

        var mismatchedRoute = new HubScanTransitionPolicy.DispatchWaveContext(
                DispatchWaveStatus.DEPARTED, "hub-a", "hub-c", true);
        assertThrows(IllegalStateException.class,
                () -> policy.validate(command(HubScanType.CONTAINER_DISPATCHED, "hub-a"), sealed, mismatchedRoute));

        var open = new HubScanTransitionPolicy.ContainerContext(
                TransportContainerStatus.OPEN, "hub-a", "hub-b", 2);
        assertThrows(IllegalStateException.class,
                () -> policy.validate(command(HubScanType.CONTAINER_DISPATCHED, "hub-a"), open, departed));
    }

    @Test
    void containerReceiptRequiresDispatchedStateAtDestination() {
        var dispatched = new HubScanTransitionPolicy.ContainerContext(
                TransportContainerStatus.DISPATCHED, "hub-a", "hub-b", 2);
        assertDoesNotThrow(() -> policy.validate(command(HubScanType.CONTAINER_RECEIVED, "hub-b"), dispatched, null));
        assertThrows(IllegalStateException.class,
                () -> policy.validate(command(HubScanType.CONTAINER_RECEIVED, "hub-a"), dispatched, null));
    }

    private HubScanCommand command(HubScanType type, String hub) {
        return new HubScanCommand(tenant, UUID.randomUUID(), "scanner", UUID.randomUUID().toString(),
                "sha256:test", shipment, "parcel-1", type, hub, "operator-1", now, now.plusSeconds(1));
    }
}
