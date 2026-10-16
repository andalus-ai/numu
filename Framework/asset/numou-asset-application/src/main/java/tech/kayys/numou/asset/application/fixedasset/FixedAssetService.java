package tech.kayys.numou.asset.application.fixedasset;

import tech.kayys.numou.asset.domain.fixedasset.*;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.spi.port.FixedAssetStorePort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service managing fixed asset acquisition, depreciation runs, and disposals.
 */
public final class FixedAssetService {

    private final FixedAssetStorePort store;

    public FixedAssetService(FixedAssetStorePort store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    public FixedAssetAggregate acquire(AssetId id, String number, String name,
                                       AssetCategory category, BigDecimal cost,
                                       BigDecimal salvage, int usefulLifeMonths,
                                       DepreciationMethod method, LocalDate date) {
        FixedAssetAggregate asset = new FixedAssetAggregate(
                id, number, name, category, cost, salvage, usefulLifeMonths, method, date);
        store.saveAsset(asset);
        return asset;
    }

    public void activate(AssetId id) {
        FixedAssetAggregate asset = get(id);
        asset.activate();
        store.saveAsset(asset);
    }

    public BigDecimal runMonthlyDepreciation(AssetId id) {
        FixedAssetAggregate asset = get(id);
        BigDecimal amount = asset.calculateMonthlyStraightLineDepreciation();
        asset.applyDepreciation(amount);
        store.saveAsset(asset);
        return amount;
    }

    public void registerBook(AssetId id, BookId bookId, DepreciationMethod method,
                             int usefulLifeMonths, BigDecimal residualValue) {
        FixedAssetAggregate asset = get(id);
        asset.registerBook(bookId, method, usefulLifeMonths, residualValue);
        store.saveAsset(asset);
    }

    public void recordImpairment(AssetId id, BigDecimal loss) {
        FixedAssetAggregate asset = get(id);
        asset.recordImpairment(loss);
        store.saveAsset(asset);
    }

    public void recordRevaluation(AssetId id, BigDecimal fairValue) {
        FixedAssetAggregate asset = get(id);
        asset.recordRevaluation(fairValue);
        store.saveAsset(asset);
    }

    // ── Lease Management (IFRS 16) ──────────────────────────────────────────

    public LeaseContract registerLease(String contractNumber, String lessor, LocalDate startDate,
                                       int termMonths, BigDecimal monthlyPayment,
                                       BigDecimal discountRateAnnual, LeaseClassification classification) {
        LeaseContract lease = new LeaseContract(
                LeaseId.generate(), contractNumber, lessor, startDate,
                termMonths, monthlyPayment, discountRateAnnual, classification
        );
        store.saveLease(lease);
        return lease;
    }

    public LeaseContract getLease(LeaseId id) {
        return store.findLease(id)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + id.value()));
    }

    // ── Construction In Progress (CIP) ──────────────────────────────────────

    public ConstructionProject createCipProject(String projectCode, String name,
                                               BigDecimal budget, LocalDate startDate) {
        ConstructionProject cip = new ConstructionProject(CipId.generate(), projectCode, name, budget, startDate);
        store.saveConstructionProject(cip);
        return cip;
    }

    public void addCipExpenditure(CipId id, BigDecimal amount) {
        ConstructionProject project = getCip(id);
        project.addExpenditure(amount);
        store.saveConstructionProject(project);
    }

    public FixedAssetAggregate capitalizeCip(CipId id, String assetNumber, AssetCategory category,
                                            DepreciationMethod method, int usefulLifeMonths,
                                            BigDecimal salvageValue) {
        ConstructionProject cip = getCip(id);
        FixedAssetAggregate asset = cip.completeAndCapitalize(
                assetNumber, category, method, usefulLifeMonths, salvageValue
        );
        store.saveConstructionProject(cip);
        store.saveAsset(asset);
        return asset;
    }

    public ConstructionProject getCip(CipId id) {
        return store.findConstructionProject(id)
                .orElseThrow(() -> new IllegalArgumentException("CIP project not found: " + id.value()));
    }

    public BigDecimal disposeAsset(AssetId id, BigDecimal proceeds) {
        FixedAssetAggregate asset = get(id);
        BigDecimal gainOrLoss = asset.dispose(proceeds);
        store.saveAsset(asset);
        return gainOrLoss;
    }

    public FixedAssetAggregate get(AssetId id) {
        return store.findAsset(id)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + id.getValue()));
    }

    public Optional<FixedAssetAggregate> find(AssetId id) { return store.findAsset(id); }
}
