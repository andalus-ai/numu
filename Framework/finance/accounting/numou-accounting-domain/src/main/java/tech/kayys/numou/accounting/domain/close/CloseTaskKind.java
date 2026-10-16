package tech.kayys.syirkah.accounting.domain.close;

/**
 * Standard classification of financial close tasks.
 */
public enum CloseTaskKind {
    SUBLEDGER_CUTOFF,
    ACCRUALS,
    DEPRECIATION,
    FX_REVALUATION,
    RECONCILIATION,
    LOCK
}
