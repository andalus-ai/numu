package tech.kayys.syirkah.kiosk.domain.valueobject;

/**
 * Status of a kiosk device.
 */
public enum KioskStatus {
    ONLINE("Online - Available for use"),
    OFFLINE("Offline - Not available"),
    MAINTENANCE("Maintenance - Being serviced"),
    ERROR("Error - Needs attention"),
    IN_USE("In Use - Currently active"),
    LOW_PAPER("Low Paper - Needs paper refill"),
    LOW_THERMAL("Low Thermal Paper - Needs thermal paper"),
    LOW_CASH("Low Cash - Cash drawer needs refill");

    private final String description;

    KioskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOperational() {
        return this == ONLINE || this == IN_USE;
    }

    public boolean requiresAttention() {
        return this == ERROR || this == LOW_PAPER || 
               this == LOW_THERMAL || this == LOW_CASH;
    }
}