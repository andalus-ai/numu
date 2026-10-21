package tech.kayys.numou.numoudomain;

/** Clauses that invalidate a syirkah contract under hukum muamalah. */
public enum ForbiddenClause {
    /** A fixed nominal profit amount promised to a partner. */
    FIXED_PROFIT_AMOUNT,
    /**
     * Capital (or return) guaranteed by the manager/partner against ordinary loss.
     */
    CAPITAL_GUARANTEE,
    /**
     * Salary for a partner/manager paid irrespective of result (not a
     * wage-contract).
     */
    MANAGER_SALARY,
    /** Loss shared in any proportion other than capital. */
    LOSS_NOT_BY_CAPITAL,
    /**
     * Capital provider interferes with the manager's management in a mudharabah.
     */
    CAPITAL_PROVIDER_CONTROL,
    /** A partner is excluded from any share of profit. */
    PROFIT_EXCLUSION
}
