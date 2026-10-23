package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.domain.Shipment;
import tech.kayys.numou.courier.domain.ShipmentRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Coordinates shipment aggregate creation and durable booking. */
public final class BookShipmentHandler {
    private final ShipmentRepository shipments;

    public BookShipmentHandler(ShipmentRepository shipments) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
    }

    public CompletionStage<Shipment> handle(BookShipmentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Shipment shipment = Shipment.draft(command.shipmentId(), command.tenantId(), command.reference(),
                command.sender(), command.recipient(), command.serviceLevel(), command.occurredAt());
        for (var parcel : command.parcels()) shipment.addParcel(parcel, command.occurredAt());
        shipment.book(command.occurredAt());
        return shipments.save(command.tenantId(), shipment);
    }
}