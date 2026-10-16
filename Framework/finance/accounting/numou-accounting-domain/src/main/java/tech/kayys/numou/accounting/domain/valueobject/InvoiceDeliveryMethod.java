package tech.kayys.syirkah.accounting.domain.valueobject;

/**
 * Methods for delivering invoices.
 */
public enum InvoiceDeliveryMethod {
    EMAIL("Email"),
    PRINT("Print"),
    PORTAL("Customer Portal"),
    API("API Integration"),
    FAX("Fax");

    private final String displayName;

    InvoiceDeliveryMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDigital() {
        return this == EMAIL || this == PORTAL || this == API;
    }
}