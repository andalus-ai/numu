package tech.kayys.numou.queuemanagement.domain;

/**
 * Queue profiles identify distinct operational semantics. They do not transfer
 * ownership of the referenced customer, case, appointment, work order, or shipment.
 */
public enum QueueProfile {
    WAITING_SERVICE,
    DISPATCH,
    OPERATIONAL_WORK
}
