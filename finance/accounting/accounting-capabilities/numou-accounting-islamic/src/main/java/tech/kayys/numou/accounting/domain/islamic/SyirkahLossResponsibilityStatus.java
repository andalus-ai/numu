package tech.kayys.numou.accounting.domain.islamic;

/** Controlled lifecycle for reviewing responsibility for a Syirkah loss. */
public enum SyirkahLossResponsibilityStatus {
    REPORTED,
    DETERMINED,
    APPROVED,
    POSTED,
    PARTIALLY_SETTLED,
    SETTLED
}
