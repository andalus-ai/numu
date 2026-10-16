package tech.kayys.syirkah.asset.spi.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Accounting Integration SPI (ASSET-25 section 39): query contract the
 * Accounting side implements later. Interface only — no CDI issue.
 */
public interface AccountingAssetPort {

    CompletionStage<Optional<AccountingAssetSnapshot>> findByOperationalAsset(String tenantId, UUID assetId);

    record AccountingAssetSnapshot(UUID accountingAssetId, String accountingAssetNumber, String currency,
            BigDecimal acquisitionCost, BigDecimal accumulatedDepreciation, BigDecimal netBookValue,
            String status, Instant capitalizationDate) {
    }
}
