package tech.kayys.numou.accounting.domain.islamic.syirkah;

/**
 * Commercial partnership form. This is deliberately separate from
 * ShariaContractType, which classifies an accounting transaction.
 *
 * The type alone does not select a legal/accounting policy. Each active
 * contract version must pin an approved loss-policy ID and version.
 */
public enum SyirkahType {
    MUDHARABAH,
    MUSYARAKAH,
    INAN,
    ABDAN,
    WUJUH,
    MUFAWADHAH
}
