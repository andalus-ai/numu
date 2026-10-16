package tech.kayys.syirkah.asset.domain.fixedasset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.asset.domain.fixedasset.strategy.*;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdvancedAssetTest — Multi-book, IFRS 16, CIP & Strategies")
class AdvancedAssetTest {

    @Test
    @DisplayName("Multi-book depreciation schedule tracks entries and residual floor")
    void testMultiBookSchedule() {
        FixedAssetAggregate asset = new FixedAssetAggregate(
                AssetId.generate(), "EQ-100", "Packaging Machine",
                AssetCategory.PLANT_AND_MACHINERY, new BigDecimal("120000.00"),
                new BigDecimal("12000.00"), 60,
                DepreciationMethod.STRAIGHT_LINE, LocalDate.of(2026, 1, 1)
        );
        asset.activate();

        // Register a secondary TAX book with 36 months accelerated life and zero residual
        asset.registerBook(BookId.TAX, DepreciationMethod.REDUCING_BALANCE, 36, BigDecimal.ZERO);
        assertNotNull(asset.getSchedule(BookId.TAX));
        assertNotNull(asset.getSchedule(BookId.CORP));

        DepreciationSchedule taxSchedule = asset.getSchedule(BookId.TAX);
        var entry1 = taxSchedule.append(new BigDecimal("3333.33"), 2026, 1);
        assertEquals(1, taxSchedule.entries().size());
        assertEquals(new BigDecimal("3333.33"), entry1.depreciationAmount());
        assertEquals(new BigDecimal("116666.67"), entry1.carryingValueAfter());
    }

    @Test
    @DisplayName("Asset components depreciate independently")
    void testAssetComponents() {
        FixedAssetAggregate aircraft = new FixedAssetAggregate(
                AssetId.generate(), "AIR-01", "Commercial Aircraft",
                AssetCategory.VEHICLES, new BigDecimal("50000000.00"),
                BigDecimal.ZERO, 240,
                DepreciationMethod.STRAIGHT_LINE, LocalDate.of(2026, 1, 1)
        );

        AssetComponent engine1 = new AssetComponent(
                ComponentId.generate(), "Engine Left CFM56",
                new BigDecimal("8000000.00"), 120, new BigDecimal("500000.00")
        );
        AssetComponent airframe = new AssetComponent(
                ComponentId.generate(), "Fuselage & Wings",
                new BigDecimal("42000000.00"), 300, new BigDecimal("2000000.00")
        );

        aircraft.addComponent(engine1);
        aircraft.addComponent(airframe);
        assertEquals(2, aircraft.components().size());

        engine1.applyDepreciation(new BigDecimal("62500.00"));
        assertEquals(new BigDecimal("62500.00"), engine1.accumulatedDepreciation());
        assertEquals(new BigDecimal("7937500.00"), engine1.bookValue());
    }

    @Test
    @DisplayName("IFRS 16 LeaseContract calculates present value and ROU asset amortization")
    void testIfrs16Lease() {
        LeaseContract lease = new LeaseContract(
                LeaseId.generate(), "LEASE-2026-001", "Property Lessor Corp",
                LocalDate.of(2026, 1, 1), 36,
                new BigDecimal("10000.00"), new BigDecimal("0.06"), // 6% annual discount rate
                LeaseClassification.FINANCE
        );

        assertTrue(lease.rightOfUseAssetValue().compareTo(new BigDecimal("300000.00")) > 0);
        assertEquals(lease.rightOfUseAssetValue(), lease.leaseLiability());

        // Amortize first month
        lease.amortizePeriod(new BigDecimal("1640.00"), new BigDecimal("8360.00"), new BigDecimal("9100.00"));
        assertTrue(lease.leaseLiability().compareTo(lease.rightOfUseAssetValue()) < 0);
        assertEquals(new BigDecimal("9100.00"), lease.accumulatedRouDepreciation());
    }

    @Test
    @DisplayName("ConstructionProject accumulates WIP expenditures and capitalizes upon completion")
    void testCipCapitalization() {
        ConstructionProject cip = new ConstructionProject(
                CipId.generate(), "CIP-PLANT-02", "New Bottling Facility",
                new BigDecimal("1000000.00"), LocalDate.of(2026, 1, 15)
        );
        assertEquals(ConstructionProject.Status.IN_PROGRESS, cip.status());

        cip.addExpenditure(new BigDecimal("250000.00"));
        cip.addExpenditure(new BigDecimal("350000.00"));
        assertEquals(new BigDecimal("600000.00"), cip.accumulatedCost());

        FixedAssetAggregate capitalizedAsset = cip.completeAndCapitalize(
                "FA-BOTTLING-01", AssetCategory.BUILDINGS,
                DepreciationMethod.STRAIGHT_LINE, 360, new BigDecimal("50000.00")
        );

        assertEquals(ConstructionProject.Status.COMPLETED, cip.status());
        assertEquals(AssetStatus.ACTIVE, capitalizedAsset.status());
        assertEquals(new BigDecimal("600000.00"), capitalizedAsset.acquisitionCost());
    }

    @Test
    @DisplayName("Depreciation strategies calculate accurate periodic charges")
    void testDepreciationStrategies() {
        BigDecimal cost = new BigDecimal("100000.00");
        BigDecimal salvage = new BigDecimal("10000.00");
        BigDecimal accumulated = BigDecimal.ZERO;

        // 1. Straight-Line
        DepreciationStrategy sl = new StraightLineStrategy();
        BigDecimal slAmt = sl.calculatePeriodDepreciation(cost, accumulated, salvage, 60, 1, Map.of());
        assertEquals(new BigDecimal("1500.0000"), slAmt);

        // 2. Reducing Balance (20% annual)
        DepreciationStrategy rb = new ReducingBalanceStrategy();
        BigDecimal rbAmt = rb.calculatePeriodDepreciation(cost, accumulated, salvage, 60, 1,
                Map.of("annualRate", new BigDecimal("0.20")));
        assertEquals(new BigDecimal("1666.7000"), rbAmt);

        // 3. Units of Production
        DepreciationStrategy uop = new UnitsOfProductionStrategy();
        BigDecimal uopAmt = uop.calculatePeriodDepreciation(cost, accumulated, salvage, 60, 1,
                Map.of("unitsProduced", new BigDecimal("5000"), "totalEstimatedUnits", new BigDecimal("100000")));
        assertEquals(new BigDecimal("4500.0000"), uopAmt);

        // 4. Sum of Years Digits
        DepreciationStrategy syd = new SumOfYearsDigitsStrategy();
        BigDecimal sydAmt = syd.calculatePeriodDepreciation(cost, accumulated, salvage, 60, 1, Map.of());
        assertTrue(sydAmt.compareTo(slAmt) > 0); // Accelerated method
    }

    @Test
    @DisplayName("Impairment (IAS 36) and Revaluation (IAS 16) adjust book values")
    void testImpairmentAndRevaluation() {
        FixedAssetAggregate asset = new FixedAssetAggregate(
                AssetId.generate(), "BLD-01", "Corporate HQ",
                AssetCategory.BUILDINGS, new BigDecimal("1000000.00"),
                BigDecimal.ZERO, 360,
                DepreciationMethod.STRAIGHT_LINE, LocalDate.of(2020, 1, 1)
        );
        asset.activate();

        // 1. Revaluation upward by 200,000
        asset.recordRevaluation(new BigDecimal("1200000.00"));
        assertEquals(new BigDecimal("200000.00"), asset.revaluationSurplus());
        assertEquals(new BigDecimal("1200000.00"), asset.bookValue());

        // 2. Impairment loss of 150,000
        asset.recordImpairment(new BigDecimal("150000.00"));
        assertEquals(ImpairmentStatus.IMPAIRED, asset.impairmentStatus());
        assertEquals(new BigDecimal("1050000.00"), asset.bookValue());
    }
}
