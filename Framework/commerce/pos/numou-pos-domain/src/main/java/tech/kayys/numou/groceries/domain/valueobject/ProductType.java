package tech.kayys.syirkah.groceries.domain.valueobject;

/**
 *  product types with specific handling requirements.
 */
public enum ProductType {
    AMBIENT("Ambient - Room temperature storage"),
    CHILLED("Chilled - Refrigerated"),
    FROZEN("Frozen - Below freezing"),
    FRESH("Fresh - Perishable, requires weight check"),
    DELI("Deli - Fresh sliced/weighed"),
    BAKERY("Bakery - Fresh baked"),
    PRODUCE("Produce - Fresh fruits/vegetables"),
    MEAT("Meat - Fresh/sealed meat products"),
    SEAFOOD("Seafood - Fresh/frozen seafood"),
    DAIRY("Dairy - Refrigerated dairy products"),
    NON_FOOD("Non-Food - General merchandise"),
    BEVERAGE("Beverage - Drinks");

    private final String description;

    ProductType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPerishable() {
        return this == FRESH || this == DELI || this == BAKERY ||
               this == PRODUCE || this == MEAT || this == SEAFOOD ||
               this == DAIRY;
    }

    public boolean requiresWeight() {
        return this == FRESH || this == DELI || this == PRODUCE ||
               this == MEAT || this == SEAFOOD;
    }

    public boolean requiresTemperatureControl() {
        return this == CHILLED || this == FROZEN || this == DAIRY ||
               this == MEAT || this == SEAFOOD;
    }
}
