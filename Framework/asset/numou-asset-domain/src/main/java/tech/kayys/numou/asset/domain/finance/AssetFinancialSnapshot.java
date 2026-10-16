package tech.kayys.syirkah.asset.domain.finance;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-model snapshot of the financial view of an asset (ASSET-25 §26).
 *
 * <p>Projected from Accounting events / the Accounting SPI — never computed by
 * Asset (Asset never calculates depreciation or journals).</p>
 */
public record AssetFinancialSnapshot(
        AssetId assetId,
        UUID accountingAssetId,
        BigDecimal acquisitionCost,
        BigDecimal accumulatedDepreciation,
        BigDecimal netBookValue,
        BigDecimal impairmentAmount,
        BigDecimal proceeds,
        String currency,
        Instant capitalizationDate,
        Instant lastDepreciationDate,
        Instant disposedAt,
        FixedAssetStatus status,
        Instant asOf
) {

    public AssetFinancialSnapshot {
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
