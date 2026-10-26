package tech.kayys.numou.vehicleservice.domain;

public enum WorkshopWorkOrderStatus {
    DRAFT,
    AWAITING_CUSTOMER_APPROVAL,
    APPROVED,
    SCHEDULED,
    IN_PROGRESS,
    ON_HOLD,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED
}
