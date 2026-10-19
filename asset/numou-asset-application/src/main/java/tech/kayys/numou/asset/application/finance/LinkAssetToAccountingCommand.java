package tech.kayys.numou.asset.application.finance;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

/** Link an operational asset to its Accounting fixed asset (ASSET-25). */
public record LinkAssetToAccountingCommand(
        String tenantId,
        AssetId assetId,
        UUID accountingAssetId,
        String accountingAssetNumber,
        String linkedBy
) implements Command {

    public LinkAssetToAccountingCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
    }
}
