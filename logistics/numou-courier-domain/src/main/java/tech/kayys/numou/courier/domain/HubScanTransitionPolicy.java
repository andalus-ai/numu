package tech.kayys.numou.courier.domain;

import java.util.Objects;

/**
 * Pure guard for hub scans against the locked operational state read by an application adapter.
 *
 * <p>The caller must load the supplied state within the command tenant and hold the relevant
 * database row locks until the scan receipt, aggregate transition and outbox event commit.
 * This policy does not load state or authorize exception overrides by itself.</p>
 */
public final class HubScanTransitionPolicy {

    public void validate(HubScanCommand command, ContainerContext container, DispatchWaveContext wave) {
        Objects.requireNonNull(command, "command must not be null");
        switch (command.type()) {
            case LOAD_DISCREPANCY -> {
                require(wave != null, "load discrepancy requires a dispatch wave");
                require(wave.status() == DispatchWaveStatus.LOADING,
                        "load discrepancy requires a wave in LOADING state");
                require(equal(command.hubReference(), wave.originHubReference()),
                        "discrepancy scan hub does not match dispatch-wave origin");
                require(command.discrepancyReason() != null && !command.discrepancyReason().isBlank(),
                        "load discrepancy requires a reason");
            }
            case DISPATCH_LOADED -> {
                require(wave != null, "dispatch-loaded scan requires a dispatch wave");
                require(wave.status() == DispatchWaveStatus.LOADING,
                        "dispatch-loaded scan requires a wave in LOADING state");
                require(wave.expectedParcel(), "parcel is not expected in the dispatch wave");
                require(equal(command.hubReference(), wave.originHubReference()),
                        "scan hub does not match dispatch-wave origin");
            }
            case CONTAINER_SEALED -> {
                require(container != null, "container-sealed scan requires a container");
                require(container.status() == TransportContainerStatus.OPEN,
                        "only an open container can be sealed");
                require(container.parcelCount() > 0, "empty container cannot be sealed");
                require(equal(command.hubReference(), container.originHubReference()),
                        "container must be sealed at its origin hub");
            }
            case CONTAINER_DISPATCHED -> {
                require(container != null, "container-dispatched scan requires a container");
                require(container.status() == TransportContainerStatus.SEALED,
                        "only a sealed container can be dispatched");
                require(equal(command.hubReference(), container.originHubReference()),
                        "container must depart from its origin hub");
                require(wave != null, "container dispatch requires a dispatch wave");
                require(wave.status() == DispatchWaveStatus.DEPARTED,
                        "container dispatch requires a departed dispatch wave");
                require(equal(container.originHubReference(), wave.originHubReference())
                                && equal(container.destinationHubReference(), wave.destinationHubReference()),
                        "container route does not match dispatch-wave route");
            }
            case CONTAINER_RECEIVED -> {
                require(container != null, "container-received scan requires a container");
                require(container.status() == TransportContainerStatus.DISPATCHED,
                        "only a dispatched container can be received");
                require(equal(command.hubReference(), container.destinationHubReference()),
                        "container must be received at its destination hub");
            }
            case HUB_RECEIVED -> {
                if (container != null) {
                    require(container.status() == TransportContainerStatus.DISPATCHED,
                            "hub receipt for a container requires DISPATCHED state");
                    require(equal(command.hubReference(), container.destinationHubReference()),
                            "container must be received at its destination hub");
                }
            }
            case PICKUP_COLLECTED, HUB_INTAKE, SORTED, EXCEPTION_RECORDED -> {
                // These scans do not imply a container/wave lifecycle transition.
            }
        }
    }

    private static boolean equal(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record ContainerContext(
            TransportContainerStatus status,
            String originHubReference,
            String destinationHubReference,
            int parcelCount) {
        public ContainerContext {
            Objects.requireNonNull(status, "status must not be null");
            require(parcelCount >= 0, "parcelCount must not be negative");
            if (originHubReference == null || originHubReference.isBlank()) {
                throw new IllegalArgumentException("originHubReference must not be blank");
            }
            if (destinationHubReference == null || destinationHubReference.isBlank()) {
                throw new IllegalArgumentException("destinationHubReference must not be blank");
            }
        }
    }

    public record DispatchWaveContext(
            DispatchWaveStatus status,
            String originHubReference,
            String destinationHubReference,
            boolean expectedParcel) {
        public DispatchWaveContext {
            Objects.requireNonNull(status, "status must not be null");
            if (originHubReference == null || originHubReference.isBlank()) {
                throw new IllegalArgumentException("originHubReference must not be blank");
            }
            if (destinationHubReference == null || destinationHubReference.isBlank()) {
                throw new IllegalArgumentException("destinationHubReference must not be blank");
            }
        }
    }
}
