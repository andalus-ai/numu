package tech.kayys.numou.courier.domain;

/** Shipment booking and terminal lifecycle states; tracking retains the detailed movement timeline. */
public enum ShipmentStatus {
    DRAFT, BOOKED, CANCELLED, DELIVERED
}