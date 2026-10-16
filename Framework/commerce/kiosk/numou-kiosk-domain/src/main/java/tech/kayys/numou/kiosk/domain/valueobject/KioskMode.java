package tech.kayys.syirkah.kiosk.domain.valueobject;

/**
 * Operating mode of the kiosk.
 */
public enum KioskMode {
    SELF_CHECKOUT("Self-Checkout - Customer scans and pays"),
    ORDER_PAYMENT("Order & Payment - Customer orders and pays"),
    PICKUP("Pickup - Customer picks up online order"),
    RETURN("Return - Customer returns items"),
    SUPPORT("Support - Assisted customer service");

    private final String description;

    KioskMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}