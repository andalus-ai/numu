package tech.kayys.numou.asset.application.meter;
import java.util.UUID;

public record GetAssetMetersQuery(String tenantId, UUID assetId) {}
