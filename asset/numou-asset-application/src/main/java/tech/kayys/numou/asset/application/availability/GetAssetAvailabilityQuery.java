package tech.kayys.numou.asset.application.availability;

import java.util.UUID;

/** Query for the availability read model of a single asset (ASSET-26 §18). */
public record GetAssetAvailabilityQuery(String tenantId, UUID assetId) {
}