package tech.kayys.syirkah.asset.spi.asset;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;
import tech.kayys.syirkah.asset.domain.valueobject.AssetStatus;
import tech.kayys.syirkah.asset.domain.valueobject.AssetType;

/** Minimal read projection exposed across bounded contexts (ASSET-10 SPI). */
public record AssetSummary(
        AssetId id,
        String tenantId,
        String assetNumber,
        String name,
        AssetType type,
        AssetStatus status
) {
}
