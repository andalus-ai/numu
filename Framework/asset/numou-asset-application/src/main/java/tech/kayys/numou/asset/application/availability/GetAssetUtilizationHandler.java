package tech.kayys.numou.asset.application.availability;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationRecord;
import tech.kayys.numou.asset.domain.repository.AssetUtilizationRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-side handler computing a utilization summary (ASSET-26 §19).
 *
 * <p>Tenant-scoped and window-bounded. The summary is derived purely from the
 * immutable observation records — no counter is stored on the aggregate.</p>
 */
public class GetAssetUtilizationHandler {

    private final AssetUtilizationRepository utilization;

    public GetAssetUtilizationHandler(AssetUtilizationRepository utilization) {
        this.utilization = Objects.requireNonNull(utilization, "utilization");
    }

    public Uni<AssetUtilizationSummary> handle(GetAssetUtilizationQuery query) {
        return Uni.createFrom()
                .completionStage(() -> utilization.findByAssetId(
                        query.tenantId(), query.assetId(), query.from(), query.to()))
                .map(records -> summarise(query, records));
    }

    static AssetUtilizationSummary summarise(
            GetAssetUtilizationQuery query, List<AssetUtilizationRecord> records) {
        long totalSeconds = 0L;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        String unit = null;
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        for (AssetUtilizationRecord record : records) {
            totalSeconds += record.durationSeconds();
            totalQuantity = totalQuantity.add(record.quantity());
            if (unit == null) {
                unit = record.unit();
            }
            byType.merge(record.type().name(), record.quantity(), BigDecimal::add);
        }
        return new AssetUtilizationSummary(
                query.assetId(), query.from(), query.to(),
                totalSeconds, totalQuantity, unit, records.size(), byType);
    }
}