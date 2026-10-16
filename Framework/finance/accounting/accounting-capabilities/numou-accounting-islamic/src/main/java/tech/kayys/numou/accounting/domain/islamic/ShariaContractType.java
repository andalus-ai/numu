package tech.kayys.syirkah.accounting.domain.islamic;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Islamic finance contract classification for Sharia-compliant accounting (PSAK 101-112 & AAOIFI).
 */
public enum ShariaContractType implements ValueObject {
    NONE("Non-Sharia / Conventional Transaction"),

    /** Cost-plus sale contract (PSAK 102) */
    MURABAHAH("Murabahah - Cost Plus Sale"),

    /** Trust-based profit-sharing partnership (PSAK 105) */
    MUDHARABAH("Mudharabah - Profit Sharing Trust"),

    /** Joint venture profit-and-loss partnership (PSAK 106) */
    MUSYARAKAH("Musyarakah - Joint Venture Equity"),

    /** Usufruct leasing / service transfer contract (PSAK 107) */
    IJARAH("Ijarah - Leasing & Service Transfer"),

    /** Forward sale with advance payment (PSAK 103) */
    SALAM("Salam - Advance Purchase Agreement"),

    /** Manufacturing / construction contract (PSAK 104) */
    ISTISHNA("Istishna - Manufacturing on Order"),

    /** Safe custody deposit contract (Wadiah Yad Dhamanah) */
    WADIAH("Wadiah - Safe Custody Deposit"),

    /** Benevolent loan with zero interest (PSAK 101) */
    QARDH("Qardhul Hasan - Benevolent Loan"),

    /** Zakat, Infaq, Shadaqah and Waqf social fund accounting (PSAK 109 & 112) */
    ZAKAT("ZISWAF - Social & Charity Fund Accounting");

    private final String description;

    ShariaContractType(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
    public boolean isShariaContract() { return this != NONE; }
}
