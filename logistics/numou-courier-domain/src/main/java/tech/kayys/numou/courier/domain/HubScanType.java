package tech.kayys.numou.courier.domain;

/** Canonical operational scan vocabulary shared by intake, sortation and line-haul. */
public enum HubScanType {
    PICKUP_COLLECTED,
    HUB_INTAKE,
    SORTED,
    DISPATCH_LOADED,
    CONTAINER_SEALED,
    CONTAINER_DISPATCHED,
    HUB_RECEIVED,
    CONTAINER_RECEIVED,
    EXCEPTION_RECORDED
}
