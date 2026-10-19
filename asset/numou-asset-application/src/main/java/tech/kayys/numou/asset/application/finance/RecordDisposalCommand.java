package tech.kayys.numou.asset.application.finance;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Record an Accounting disposal (ASSET-25 §22). Gain/loss stays in Accounting. */
public record RecordDisposalCommand(
        String tenantId,
        AssetId assetId,
        UUID accountingAssetId,
        BigDecimal proceeds,
        String currency,
        UUID eventId
) implements Command {

    public RecordDisposalCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
    }
}
