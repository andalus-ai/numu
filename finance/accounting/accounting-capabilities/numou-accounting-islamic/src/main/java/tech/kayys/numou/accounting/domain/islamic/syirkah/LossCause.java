package tech.kayys.numou.accounting.domain.islamic.syirkah;

/**
 * Classification of a loss event. Non-ordinary causes require a separately
 * evidenced determination and are never silently converted into a partner debt.
 */
public enum LossCause {
    NOT_APPLICABLE,
    ORDINARY_BUSINESS,
    FORCE_MAJEURE,
    NEGLIGENCE,
    MISCONDUCT,
    CONTRACT_BREACH,
    UNDETERMINED
}
