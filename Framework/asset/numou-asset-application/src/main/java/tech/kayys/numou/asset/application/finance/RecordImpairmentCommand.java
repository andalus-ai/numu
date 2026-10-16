package tech.kayys.numou.asset.application.finance;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Record an Accounting impairment (ASSET-25 §24). The amount is not duplicated in Asset. */
public record RecordImpairmentCommand(
        String tenantId,
        AssetId assetId,
        UUID accountingAssetId,
        BigDecimal impairmentAmount,
        String currency,
        UUID eventId
) implements Command {

    public RecordImpairmentCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
    }
}
