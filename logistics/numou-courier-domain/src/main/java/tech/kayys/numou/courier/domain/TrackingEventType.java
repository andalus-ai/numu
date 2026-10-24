package tech.kayys.numou.courier.domain;

/** Stable operational vocabulary for customer-visible tracking and internal custody scans. */
public enum TrackingEventType {
    SHIPMENT_BOOKED,
    PICKED_UP,
    ARRIVED_AT_HUB,
    DEPARTED_HUB,
    SORTED_AT_HUB,
    LOADED_FOR_LINEHAUL,
    LINEHAUL_DEPARTED,
    LINEHAUL_ARRIVED,
    ARRIVED_AT_DESTINATION_DEPOT,
    OUT_FOR_DELIVERY,
    DELIVERY_ATTEMPTED,
    DELIVERY_FAILED,
    DELIVERED,
    HELD,
    ADDRESS_CORRECTED,
    RETURN_INITIATED,
    RETURN_IN_TRANSIT,
    RETURNED_TO_SENDER,
    EXCEPTION_RECORDED
}
