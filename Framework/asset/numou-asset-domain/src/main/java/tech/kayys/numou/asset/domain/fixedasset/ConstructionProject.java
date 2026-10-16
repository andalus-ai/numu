package tech.kayys.syirkah.asset.domain.fixedasset;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Construction In Progress (CIP) project aggregate.
 * Accumulates fabrication/building costs until commissioning, then capitalizes to FixedAsset.
 */
public final class ConstructionProject {

    public enum Status {
        IN_PROGRESS,
        SUSPENDED,
        COMPLETED
    }

    private final CipId id;
    private final String projectCode;
    private final String name;
    private final BigDecimal budget;
    private final LocalDate startDate;
    private BigDecimal accumulatedCost;
    private Status status;

    public ConstructionProject(CipId id, String projectCode, String name, BigDecimal budget, LocalDate startDate) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.projectCode = Objects.requireNonNull(projectCode, "projectCode must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.budget = Objects.requireNonNull(budget, "budget must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.accumulatedCost = BigDecimal.ZERO;
        this.status = Status.IN_PROGRESS;
    }

    public void addExpenditure(BigDecimal amount) {
        if (status != Status.IN_PROGRESS) {
            throw new IllegalStateException("Cannot add expenditure to CIP project in status: " + status);
        }
        if (amount.signum() <= 0) throw new IllegalArgumentException("Expenditure amount must be > 0");
        this.accumulatedCost = this.accumulatedCost.add(amount);
    }

    public void suspend() {
        this.status = Status.SUSPENDED;
    }

    public void resume() {
        this.status = Status.IN_PROGRESS;
    }

    /**
     * Capitalizes the CIP project into a new FixedAssetAggregate.
     */
    public FixedAssetAggregate completeAndCapitalize(String assetNumber,
                                                     AssetCategory category,
                                                     DepreciationMethod method,
                                                     int usefulLifeMonths,
                                                     BigDecimal salvageValue) {
        if (status == Status.COMPLETED) {
            throw new IllegalStateException("CIP project is already completed");
        }
        if (accumulatedCost.signum() <= 0) {
            throw new IllegalStateException("Cannot capitalize CIP project with zero accumulated cost");
        }
        this.status = Status.COMPLETED;

        FixedAssetAggregate asset = new FixedAssetAggregate(
                AssetId.generate(),
                assetNumber,
                this.name,
                category,
                this.accumulatedCost,
                salvageValue,
                usefulLifeMonths,
                method,
                LocalDate.now()
        );
        asset.activate();
        return asset;
    }

    public CipId id() { return id; }
    public String projectCode() { return projectCode; }
    public String name() { return name; }
    public BigDecimal budget() { return budget; }
    public LocalDate startDate() { return startDate; }
    public BigDecimal accumulatedCost() { return accumulatedCost; }
    public Status status() { return status; }
}
