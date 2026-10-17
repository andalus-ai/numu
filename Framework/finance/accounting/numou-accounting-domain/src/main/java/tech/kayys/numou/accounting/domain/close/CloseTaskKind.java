package tech.kayys.numou.accounting.domain.close;

/**
 * Standard classification of financial close tasks.
 */
public enum CloseTaskKind {
    SUBLEDGER_CUTOFF,
    ACCRUALS,
    DEPRECIATION,
    FX_REVALUATION,
    RECONCILIATION,
    SYIRKAH_RESULT,
    SYIRKAH_ALLOCATION,
    SYIRKAH_DISTRIBUTION,
    LOCK
}
