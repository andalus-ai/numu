package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.domain.*;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Coordinates courier assignment and persistence of the delivery task. */
public final class AssignDeliveryTaskHandler {
    private final ShipmentRepository shipments;
    private final DeliveryTaskRepository tasks;

    public AssignDeliveryTaskHandler(ShipmentRepository shipments, DeliveryTaskRepository tasks) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
        this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
    }

    public CompletionStage<DeliveryTask> handle(AssignDeliveryTaskCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return shipments.findById(command.tenantId(), command.shipmentId()).thenCompose(found -> {
            Shipment shipment = found.orElseThrow(() -> new IllegalArgumentException("shipment not found in tenant"));
            if (shipment.status() != ShipmentStatus.BOOKED)
                throw new IllegalStateException("only booked shipments may be assigned for delivery");
            DeliveryTask task = DeliveryTask.assign(command.taskId(), command.tenantId(), command.shipmentId(),
                    command.courierReference(), command.assignedBy(), command.assignedAt());
            return tasks.save(command.tenantId(), task);
        });
    }
}
