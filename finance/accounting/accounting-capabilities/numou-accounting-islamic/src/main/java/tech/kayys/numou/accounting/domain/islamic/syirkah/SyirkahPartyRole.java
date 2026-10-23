package tech.kayys.numou.accounting.domain.islamic.syirkah;

/** Contractual contribution/authority role; not an identity or authorization role. */
public enum SyirkahPartyRole {
    CAPITAL_PROVIDER,
    MANAGING_PARTNER,
    CAPITAL_AND_MANAGING_PARTNER,
    WORKING_PARTNER,
    CREDIT_PARTNER;

    public boolean contributesCapitalByRole() {
        return this == CAPITAL_PROVIDER || this == CAPITAL_AND_MANAGING_PARTNER;
    }

    public boolean managesBusinessByRole() {
        return this == MANAGING_PARTNER
                || this == CAPITAL_AND_MANAGING_PARTNER
                || this == WORKING_PARTNER;
    }
}
