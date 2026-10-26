package tech.kayys.numou.vehicleservice.domain;

import java.util.Objects;

/**
 * Workshop-owned catalogue definition. Pricing and appointment capacity are deliberately owned
 * by their respective capabilities; this aggregate only owns service identity and duration.
 */
public final class ServiceOffering {
    private final ServiceOfferingId id;
    private final String tenantId;
    private final String code;
    private String name;
    private String description;
    private ServiceCategory category;
    private int estimatedDurationMinutes;
    private ServiceOfferingStatus status;

    private ServiceOffering(ServiceOfferingId id, String tenantId, String code, String name,
                            String description, ServiceCategory category, int durationMinutes) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = requiredText(tenantId, "tenantId");
        this.code = requiredText(code, "code");
        this.name = requiredText(name, "name");
        this.description = description == null ? "" : description.trim();
        this.category = Objects.requireNonNull(category, "category");
        this.estimatedDurationMinutes = positiveDuration(durationMinutes);
        this.status = ServiceOfferingStatus.DRAFT;
    }

    public static ServiceOffering create(ServiceOfferingId id, String tenantId, String code, String name,
                                         String description, ServiceCategory category, int durationMinutes) {
        return new ServiceOffering(id, tenantId, code, name, description, category, durationMinutes);
    }

    public void revise(String name, String description, ServiceCategory category, int durationMinutes) {
        requireNotRetired();
        this.name = requiredText(name, "name");
        this.description = description == null ? "" : description.trim();
        this.category = Objects.requireNonNull(category, "category");
        this.estimatedDurationMinutes = positiveDuration(durationMinutes);
    }

    public void activate() {
        if (status == ServiceOfferingStatus.RETIRED) {
            throw new IllegalStateException("A retired service offering cannot be activated");
        }
        status = ServiceOfferingStatus.ACTIVE;
    }

    public void retire() {
        if (status == ServiceOfferingStatus.RETIRED) {
            return;
        }
        status = ServiceOfferingStatus.RETIRED;
    }

    private void requireNotRetired() {
        if (status == ServiceOfferingStatus.RETIRED) {
            throw new IllegalStateException("A retired service offering cannot be revised");
        }
    }

    private static int positiveDuration(int value) {
        if (value <= 0) throw new IllegalArgumentException("durationMinutes must be positive");
        return value;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public ServiceOfferingId id() { return id; }
    public String tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public String description() { return description; }
    public ServiceCategory category() { return category; }
    public int estimatedDurationMinutes() { return estimatedDurationMinutes; }
    public ServiceOfferingStatus status() { return status; }
}
