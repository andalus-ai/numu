package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.domain.DeliveryTaskId;
import tech.kayys.numou.courier.domain.ShipmentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/** Assigns a booked shipment to a courier; actor and tenant come from authenticated application context. */
public record AssignDeliveryTaskCommand(TenantId tenantId, DeliveryTaskId taskId, ShipmentId shipmentId,
                                       String courierReference, String assignedBy, Instant assignedAt) {
    public AssignDeliveryTaskCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        if (courierReference == null || courierReference.isBlank()) throw new IllegalArgumentException("courierReference must not be blank");
        if (assignedBy == null || assignedBy.isBlank()) throw new IllegalArgumentException("assignedBy must not be blank");
    }
}
