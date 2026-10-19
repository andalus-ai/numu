package tech.kayys.numou.asset.application.finance;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Record an Accounting capitalization against the linked asset (ASSET-25 §16). */
public record RecordCapitalizationCommand(
        String tenantId,
        AssetId assetId,
        UUID accountingAssetId,
        String accountingAssetNumber,
        BigDecimal acquisitionCost,
        String currency,
        Instant capitalizationDate,
        UUID eventId
) implements Command {

    public RecordCapitalizationCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
    }
}
