package tech.kayys.syirkah.accounting.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Supported financial accounting compliance standards.
 */
public enum ComplianceStandard implements ValueObject {
    /** International Financial Reporting Standards */
    IFRS("International Financial Reporting Standards", "Global"),

    /** US Generally Accepted Accounting Principles */
    US_GAAP("US Generally Accepted Accounting Principles", "United States"),

    /** Standar Akuntansi Keuangan Indonesia (SAK Umum & SAK Entitas Privat) */
    SAK_INDONESIA("Standar Akuntansi Keuangan Indonesia", "Indonesia"),

    /** SAK Syariah (PSAK 101-112) & AAOIFI Sharia Accounting Standards */
    SHARIA_AAOIFI_SAK("SAK Syariah & AAOIFI Standards", "Sharia Compliant"),

    /** Dual-ledger hybrid reporting (Conventional + Sharia Window) */
    HYBRID("Hybrid Dual-Ledger Multi-Standard", "Universal");

    private final String displayName;
    private final String jurisdiction;

    ComplianceStandard(String displayName, String jurisdiction) {
        this.displayName = displayName;
        this.jurisdiction = jurisdiction;
    }

    public String getDisplayName() { return displayName; }
    public String getJurisdiction() { return jurisdiction; }
    public boolean isSharia() { return this == SHARIA_AAOIFI_SAK || this == HYBRID; }
}
