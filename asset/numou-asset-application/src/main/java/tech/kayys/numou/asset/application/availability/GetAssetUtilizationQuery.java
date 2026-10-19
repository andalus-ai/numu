package tech.kayys.numou.asset.application.availability;

import java.time.Instant;
import java.util.UUID;

/**
 * Query for a utilization summary over a time window (ASSET-26 §19).
 * {@code from}/{@code to} may be null for an open window.
 */
public record GetAssetUtilizationQuery(String tenantId, UUID assetId, Instant from, Instant to) {
}