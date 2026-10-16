package tech.kayys.syirkah.purchasing.domain.valueobject;

/**
 * Contract type enumeration.
 */
public enum ContractType {
    PURCHASE("Purchase Agreement"),
    SALES("Sales Agreement"),
    SERVICE("Service Agreement"),
    EMPLOYMENT("Employment Contract"),
    NDA("Non-Disclosure Agreement"),
    LEASE("Lease Agreement"),
    LICENSE("License Agreement"),
    PARTNERSHIP("Partnership Agreement"),
    CONSULTING("Consulting Agreement"),
    MAINTENANCE("Maintenance Agreement"),
    SUBSCRIPTION("Subscription Agreement"),
    DISTRIBUTION("Distribution Agreement"),
    MANUFACTURING("Manufacturing Agreement"),
    SUPPLY("Supply Agreement"),
    OUTSOURCING("Outsourcing Agreement"),
    JOINT_VENTURE("Joint Venture Agreement"),
    FRANCHISE("Franchise Agreement"),
    OTHER("Other");

    private final String displayName;

    ContractType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCommercial() {
        return this == PURCHASE || this == SALES || this == SUPPLY || this == DISTRIBUTION;
    }

    public boolean isService() {
        return this == SERVICE || this == CONSULTING || this == MAINTENANCE || this == OUTSOURCING;
    }

    public boolean isLegal() {
        return this == NDA || this == PARTNERSHIP || this == JOINT_VENTURE;
    }
}
