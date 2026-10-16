package tech.kayys.syirkah.accounting.domain.ledger;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Classification of financial ledgers for multi-ledger accounting.
 */
public enum LedgerType implements ValueObject {
    /** Primary operational and corporate ledger */
    PRIMARY("Primary Corporate Ledger"),

    /** Local statutory tax ledger (e.g., Indonesian Pajak) */
    TAX("Statutory Tax Ledger"),

    /** International Financial Reporting Standards ledger */
    IFRS("IFRS Reporting Ledger"),

    /** Internal management and cost accounting ledger */
    MANAGEMENT("Management Reporting Ledger"),

    /** Sharia-compliant parallel ledger (Islamic window) */
    SHARIAH("Sharia Compliant Ledger");

    private final String description;

    LedgerType(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
