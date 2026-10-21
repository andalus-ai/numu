package tech.kayys.numou.numoudomain;

/** Role of a partner inside a contract. */
public enum PartnerRole {
    /**
     * Ordinary partner (inan/mufawadhah/abdan/wujuh): may contribute capital and/or
     * work.
     */
    PARTNER,
    /** Shahibul mal: capital provider in a mudharabah, does not manage. */
    CAPITAL_PROVIDER,
    /** Mudharib: manager in a mudharabah, contributes work only. */
    MANAGER
}
