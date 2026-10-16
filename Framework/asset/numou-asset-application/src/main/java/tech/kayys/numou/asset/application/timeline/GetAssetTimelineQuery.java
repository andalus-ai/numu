package tech.kayys.numou.asset.application.timeline;

import java.util.UUID;

/** Query for one page of the unified asset timeline (ASSET-27 §3). */
public record GetAssetTimelineQuery(String tenantId, UUID assetId, AssetTimelineFilter filter) {
}