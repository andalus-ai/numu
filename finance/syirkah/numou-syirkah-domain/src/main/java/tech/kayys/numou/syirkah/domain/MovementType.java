package tech.kayys.numou.syirkah.domain;

/**
 * Kinds of append-only partner movements and their signed effect on the four
 * buckets of a partner position: capital basis, capital balance, profit payable
 * and manager liability.
 */
public enum MovementType {
    CONTRIBUTION(1, 1, 0, 0),
    CAPITAL_RETURN(-1, -1, 0, 0),
    LOSS_ALLOCATED(0, -1, 0, 0),
    CAPITAL_RESTORED(0, 1, 0, 0),
    PROFIT_ALLOCATED(0, 0, 1, 0),
    PROFIT_RETAINED(1, 1, -1, 0),
    PROFIT_DISTRIBUTED(0, 0, -1, 0),
    MANAGER_LOSS_LIABILITY(0, 0, 0, 1),
    LIABILITY_SETTLED(0, 0, 0, -1),
    /** Liquidation: capital deficit a partner owes becomes a liability (capital reset to zero). */
    DEFICIT_TO_LIABILITY(0, 1, 0, 1),
    /** Correction after distribution: profit paid out in excess of the corrected entitlement becomes owed back. */
    OVERPAYMENT_TO_LIABILITY(0, 0, 1, 1),
    /** Memo movement that zeroes the remaining capital basis at liquidation / revision. */
    BASIS_CLOSED(-1, 0, 0, 0);

    private final int basis;
    private final int capital;
    private final int payable;
    private final int liability;

    MovementType(int basis, int capital, int payable, int liability) {
        this.basis = basis;
        this.capital = capital;
        this.payable = payable;
        this.liability = liability;
    }

    public int basisSign() {
        return basis;
    }

    public int capitalSign() {
        return capital;
    }

    public int payableSign() {
        return payable;
    }

    public int liabilitySign() {
        return liability;
    }
}
