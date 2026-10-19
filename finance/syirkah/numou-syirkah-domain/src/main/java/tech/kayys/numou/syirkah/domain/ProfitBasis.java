package tech.kayys.numou.syirkah.domain;

/** What profit is computed on. Only {@link #NET_PROFIT} is permitted by fiqh. */
public enum ProfitBasis {
    /** Revenue minus direct, operating and other costs (laba bersih). */
    NET_PROFIT,
    /** Sharing on gross revenue: forbidden (invalid contract). */
    REVENUE
}
