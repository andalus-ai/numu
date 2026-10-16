package tech.kayys.syirkah.asset.domain.fixedasset;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FixedAssetAggregateTest {

    @Test
    void depreciation_and_disposal_lifecycle() {
        var id = AssetId.generate();
        var asset = new FixedAssetAggregate(
                id, "FA-001", "Delivery Van", AssetCategory.VEHICLES,
                new BigDecimal("60000"), new BigDecimal("0"), 60,
                DepreciationMethod.STRAIGHT_LINE, LocalDate.of(2026, 1, 1));

        assertEquals(AssetStatus.DRAFT, asset.status());
        assertEquals(new BigDecimal("60000"), asset.bookValue());

        asset.activate();
        assertEquals(AssetStatus.ACTIVE, asset.status());

        // Monthly depr = 60000 / 60 = 1000
        BigDecimal monthly = asset.calculateMonthlyStraightLineDepreciation();
        assertEquals(new BigDecimal("1000.0000"), monthly);

        asset.applyDepreciation(new BigDecimal("1000"));
        assertEquals(AssetStatus.DEPRECIATING, asset.status());
        assertEquals(new BigDecimal("59000"), asset.bookValue());

        // Dispose after 1 month for 55000 -> loss of 4000
        BigDecimal gainLoss = asset.dispose(new BigDecimal("55000"));
        assertEquals(new BigDecimal("-4000"), gainLoss);
        assertEquals(AssetStatus.DISPOSED, asset.status());
    }

    @Test
    void full_depreciation_status() {
        var id = AssetId.generate();
        var asset = new FixedAssetAggregate(
                id, "FA-002", "Laptop", AssetCategory.IT_EQUIPMENT,
                new BigDecimal("2000"), new BigDecimal("200"), 2,
                DepreciationMethod.STRAIGHT_LINE, LocalDate.of(2026, 1, 1));

        asset.activate();
        asset.applyDepreciation(new BigDecimal("1800")); // depreciated down to salvage value (200)

        assertEquals(AssetStatus.FULLY_DEPRECIATED, asset.status());
        assertEquals(new BigDecimal("200"), asset.bookValue());
    }
}
