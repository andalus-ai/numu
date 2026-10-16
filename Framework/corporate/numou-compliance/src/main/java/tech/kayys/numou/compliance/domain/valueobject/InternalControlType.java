package tech.kayys.syirkah.compliance.domain.valueobject;

/**
 * Types of internal controls.
 */
public enum InternalControlType {
    SEGREGATION_OF_DUTIES("Segregation of Duties"),
    AUTHORIZATION("Authorization"),
    RECORD_KEEPING("Record Keeping"),
    REVIEW("Review and Reconciliation"),
    PHYSICAL_CONTROLS("Physical Controls"),
    ACCESS_CONTROLS("Access Controls"),
    IT_CONTROLS("IT Controls"),
    BUDGETARY_CONTROLS("Budgetary Controls"),
    PREVENTIVE_CONTROLS("Preventive Controls"),
    DETECTIVE_CONTROLS("Detective Controls"),
    CORRECTIVE_CONTROLS("Corrective Controls");

    private final String description;

    InternalControlType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
