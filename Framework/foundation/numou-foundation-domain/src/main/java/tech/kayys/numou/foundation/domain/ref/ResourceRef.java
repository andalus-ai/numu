package tech.kayys.numou.foundation.domain.ref;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Generic typed resource reference for scheduling, asset allocation, and assignments.
 * Examples: (WORKER, uuid), (VEHICLE, uuid), (ROOM, uuid), (EQUIPMENT, uuid).
 */
public record ResourceRef(
        String resourceType,
        UUID resourceId
) implements Serializable {

    public ResourceRef {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
    }

    public static ResourceRef of(String resourceType, UUID resourceId) {
        return new ResourceRef(resourceType, resourceId);
    }

    public static ResourceRef ofWorker(UUID workerId) {
        return new ResourceRef("WORKER", workerId);
    }

    public static ResourceRef ofVehicle(UUID vehicleId) {
        return new ResourceRef("VEHICLE", vehicleId);
    }

    public static ResourceRef ofRoom(UUID roomId) {
        return new ResourceRef("ROOM", roomId);
    }
}
