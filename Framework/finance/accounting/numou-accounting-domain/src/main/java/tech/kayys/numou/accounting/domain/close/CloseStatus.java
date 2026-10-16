package tech.kayys.syirkah.accounting.domain.close;

/**
 * Lifecycle states of a financial period close cycle.
 */
public enum CloseStatus {
    OPEN,
    VALIDATING,
    APPROVED,
    LOCKED
}
