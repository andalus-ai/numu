package tech.kayys.numou.fulfillment.application;

public enum AllocationSagaStatus {
    RESERVING,
    RESERVATIONS_READY,
    CONFIRMING,
    COMPLETED,
    COMPENSATION_REQUIRED,
    COMPENSATED
}
