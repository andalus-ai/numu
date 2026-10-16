package tech.kayys.syirkah.project.domain.commercial;

/**
 * Advance / down payment lifecycle.
 *
 * Project only owns entitlement and application of the advance -
 * the cash itself is observed from Billing/AR via events.
 */
public enum AdvanceStatus {

    RECEIVABLE,

    RECEIVED,

    PARTIALLY_APPLIED,

    FULLY_APPLIED,

    REFUNDED,

    CANCELLED
}