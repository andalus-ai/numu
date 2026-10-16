package tech.kayys.syirkah.asset.domain.finance;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Integration link: which Accounting fixed asset represents an operational
 * Asset (ASSET-25 §5). One-to-one in the first version; relaxing to
 * one-to-many requires a deliberate domain change.
 */
public record AssetAccountingLink(
        AssetAccountingLinkId id,
        String tenantId,
        AssetId assetId,
        UUID accountingAssetId,
        String accountingAssetNumber,
        Instant linkedAt,
        String linkedBy,
        boolean active
) {

    public AssetAccountingLink {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(accountingAssetId, "accountingAssetId cannot be null");
        Objects.requireNonNull(linkedAt, "linkedAt cannot be null");
    }
}
