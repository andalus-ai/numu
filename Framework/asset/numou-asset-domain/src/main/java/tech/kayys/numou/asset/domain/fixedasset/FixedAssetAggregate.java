package tech.kayys.syirkah.asset.domain.fixedasset;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregate root managing fixed asset capitalization, depreciation, and disposal.
 */
public final class FixedAssetAggregate {

    private final AssetId id;
    private final String assetNumber;
    private final String name;
    private final AssetCategory category;
    private final BigDecimal acquisitionCost;
    private final BigDecimal salvageValue;
    private final int usefulLifeMonths;
    private final DepreciationMethod depreciationMethod;
    private final LocalDate acquisitionDate;
    private AssetStatus status;
    private BigDecimal accumulatedDepreciation;
    private ImpairmentStatus impairmentStatus;
    private BigDecimal accumulatedImpairment;
    private BigDecimal revaluationSurplus;

    private final java.util.Map<BookId, DepreciationSchedule> books = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<AssetComponent> components = new java.util.concurrent.CopyOnWriteArrayList<>();

    public FixedAssetAggregate(AssetId id, String assetNumber, String name,
                               AssetCategory category, BigDecimal acquisitionCost,
                               BigDecimal salvageValue, int usefulLifeMonths,
                               DepreciationMethod depreciationMethod, LocalDate acquisitionDate) {
        this.id = Objects.requireNonNull(id);
        this.assetNumber = Objects.requireNonNull(assetNumber);
        this.name = Objects.requireNonNull(name);
        this.category = Objects.requireNonNull(category);
        this.acquisitionCost = Objects.requireNonNull(acquisitionCost);
        this.salvageValue = salvageValue == null ? BigDecimal.ZERO : salvageValue;
        this.usefulLifeMonths = usefulLifeMonths;
        this.depreciationMethod = Objects.requireNonNull(depreciationMethod);
        this.acquisitionDate = Objects.requireNonNull(acquisitionDate);
        this.status = AssetStatus.DRAFT;
        this.accumulatedDepreciation = BigDecimal.ZERO;
        this.impairmentStatus = ImpairmentStatus.NOT_IMPAIRED;
        this.accumulatedImpairment = BigDecimal.ZERO;
        this.revaluationSurplus = BigDecimal.ZERO;

        if (acquisitionCost.signum() <= 0) throw new IllegalArgumentException("Acquisition cost must be positive");
        if (usefulLifeMonths <= 0) throw new IllegalArgumentException("Useful life months must be > 0");

        // Initialize default corporate depreciation schedule
        this.books.put(BookId.CORP, new DepreciationSchedule(
                BookId.CORP, depreciationMethod, usefulLifeMonths, this.salvageValue, acquisitionCost
        ));
    }

    public void activate() {
        if (status != AssetStatus.DRAFT) throw new IllegalStateException("Only DRAFT assets can be activated: " + status);
        this.status = AssetStatus.ACTIVE;
    }

    public BigDecimal bookValue() {
        return acquisitionCost.add(revaluationSurplus).subtract(accumulatedDepreciation).subtract(accumulatedImpairment);
    }

    public BigDecimal calculateMonthlyStraightLineDepreciation() {
        BigDecimal depreciableBase = acquisitionCost.add(revaluationSurplus).subtract(salvageValue);
        if (depreciableBase.signum() <= 0) return BigDecimal.ZERO;
        return depreciableBase.divide(BigDecimal.valueOf(usefulLifeMonths), 4, RoundingMode.HALF_UP);
    }

    public void applyDepreciation(BigDecimal amount) {
        if (status != AssetStatus.ACTIVE && status != AssetStatus.DEPRECIATING) {
            throw new IllegalStateException("Cannot depreciate asset in status: " + status);
        }
        if (amount.signum() <= 0) throw new IllegalArgumentException("Depreciation amount must be positive");

        BigDecimal remainingDepreciable = bookValue().subtract(salvageValue);
        BigDecimal actualDepr = amount.min(remainingDepreciable);

        this.accumulatedDepreciation = this.accumulatedDepreciation.add(actualDepr);
        if (bookValue().compareTo(salvageValue) <= 0) {
            this.status = AssetStatus.FULLY_DEPRECIATED;
        } else {
            this.status = AssetStatus.DEPRECIATING;
        }
    }

    // ── Multi-Book Support ──────────────────────────────────────────────────

    public void registerBook(BookId bookId, DepreciationMethod method, int usefulLifeMonths, BigDecimal residualValue) {
        this.books.put(bookId, new DepreciationSchedule(bookId, method, usefulLifeMonths, residualValue, this.acquisitionCost));
    }

    public DepreciationSchedule getSchedule(BookId bookId) {
        return this.books.get(bookId);
    }

    public java.util.Map<BookId, DepreciationSchedule> books() {
        return java.util.Map.copyOf(books);
    }

    // ── Component Accounting ────────────────────────────────────────────────

    public void addComponent(AssetComponent component) {
        Objects.requireNonNull(component, "component must not be null");
        this.components.add(component);
    }

    public java.util.List<AssetComponent> components() {
        return java.util.List.copyOf(components);
    }

    // ── Impairment & Revaluation (IAS 36 / IAS 16) ──────────────────────────

    public void recordImpairment(BigDecimal impairmentLoss) {
        if (impairmentLoss.signum() <= 0) throw new IllegalArgumentException("impairmentLoss must be > 0");
        this.accumulatedImpairment = this.accumulatedImpairment.add(impairmentLoss);
        this.impairmentStatus = ImpairmentStatus.IMPAIRED;
    }

    public void recordRevaluation(BigDecimal fairValue) {
        BigDecimal currentCarrying = bookValue();
        if (fairValue.compareTo(currentCarrying) > 0) {
            this.revaluationSurplus = fairValue.subtract(currentCarrying);
        }
    }

    public BigDecimal dispose(BigDecimal proceeds) {
        if (status == AssetStatus.DISPOSED) throw new IllegalStateException("Asset already disposed");
        BigDecimal currentBookValue = bookValue();
        this.status = AssetStatus.DISPOSED;
        // Gain (positive) or Loss (negative)
        return proceeds.subtract(currentBookValue);
    }

    public AssetId id() { return id; }
    public String assetNumber() { return assetNumber; }
    public String name() { return name; }
    public AssetCategory category() { return category; }
    public BigDecimal acquisitionCost() { return acquisitionCost; }
    public BigDecimal salvageValue() { return salvageValue; }
    public int usefulLifeMonths() { return usefulLifeMonths; }
    public DepreciationMethod depreciationMethod() { return depreciationMethod; }
    public LocalDate acquisitionDate() { return acquisitionDate; }
    public AssetStatus status() { return status; }
    public BigDecimal accumulatedDepreciation() { return accumulatedDepreciation; }
    public ImpairmentStatus impairmentStatus() { return impairmentStatus; }
    public BigDecimal accumulatedImpairment() { return accumulatedImpairment; }
    public BigDecimal revaluationSurplus() { return revaluationSurplus; }
}
