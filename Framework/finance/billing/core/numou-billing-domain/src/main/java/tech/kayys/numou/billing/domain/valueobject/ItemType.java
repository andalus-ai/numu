package tech.kayys.numou.billing.domain.valueobject;

public enum ItemType {
    SUBSCRIPTION("Recurring Subscription Fee"),
    USAGE("Metered Usage Fee"),
    ONE_TIME("One-time Purchase / Fee"),
    PHYSICAL_GOOD("Physical Retail / Grocery Product"),
    SERVICE("Service / Labor Charge"),
    SERVICE_CHARGE("Dine-in Service Charge"),
    TAX("Sales / Value Added Tax"),
    DISCOUNT("Discount / Voucher Deduction"),
    DEPOSIT("Security / Bottle Deposit");

    private final String description;

    ItemType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
