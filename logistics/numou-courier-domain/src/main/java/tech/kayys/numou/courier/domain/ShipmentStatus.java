package tech.kayys.numou.courier.domain;

/** Booking lifecycle; physical movement is recorded separately by tracking events. */
public enum ShipmentStatus {
    DRAFT, BOOKED, CANCELLED, DELIVERED
}