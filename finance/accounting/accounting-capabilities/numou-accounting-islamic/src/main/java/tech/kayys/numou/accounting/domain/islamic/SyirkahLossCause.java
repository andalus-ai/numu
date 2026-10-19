package tech.kayys.numou.accounting.domain.islamic;

/** Classification of a loss event; classification does not itself authorize a journal. */
public enum SyirkahLossCause {
    ORDINARY_BUSINESS,
    FORCE_MAJEURE,
    NEGLIGENCE,
    MISCONDUCT,
    BREACH_OF_CONTRACT,
    UNDETERMINED
}
