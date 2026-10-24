package tech.kayys.numou.courier.domain;

/** Delivery execution lifecycle; shipment booking status remains a separate concern. */
public enum DeliveryTaskStatus {
    ASSIGNED, OUT_FOR_DELIVERY, DELIVERY_ATTEMPTED, DELIVERED, FAILED, CANCELLED
}
