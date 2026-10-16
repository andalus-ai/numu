package tech.kayys.syirkah.accounting.domain.maintenance;

import java.math.BigDecimal;
import java.util.Objects;

public final class MaintenancePlan {
    private final MaintenancePlanId id;
    private final String assetId;
    private final String title;
    private final int frequencyDays;
    private final MaintenanceType type;
    private final double standardHours;
    private final BigDecimal estimatedCost;

    public MaintenancePlan(MaintenancePlanId id, String assetId, String title, int frequencyDays,
                           MaintenanceType type, double standardHours, BigDecimal estimatedCost) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.assetId = Objects.requireNonNull(assetId, "assetId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.frequencyDays = Math.max(1, frequencyDays);
        this.type = Objects.requireNonNullElse(type, MaintenanceType.PREVENTIVE);
        this.standardHours = Math.max(0, standardHours);
        this.estimatedCost = Objects.requireNonNullElse(estimatedCost, BigDecimal.ZERO);
    }

    public MaintenancePlanId id() { return id; }
    public String assetId() { return assetId; }
    public String title() { return title; }
    public int frequencyDays() { return frequencyDays; }
    public MaintenanceType type() { return type; }
    public double standardHours() { return standardHours; }
    public BigDecimal estimatedCost() { return estimatedCost; }
}
