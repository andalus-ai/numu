package tech.kayys.numou.asset.domain.warranty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain eligibility evaluation (ASSET-23 §§32-34).
 */
public final class WarrantyEligibility {

    private WarrantyEligibility() {}

    public record EligibilityRequest(
            AssetWarranty warranty,
            UUID componentAssetId,
            String exclusionCode,
            Instant now,
            BigDecimal meterValue
    ) {}

    public record EligibilityResult(
            boolean eligible,
            List<String> reasons,
            UUID warrantyId,
            UUID coverageId
    ) {}

    public static EligibilityResult evaluate(EligibilityRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        List<String> reasons = new ArrayList<>();
        AssetWarranty warranty = Objects.requireNonNull(request.warranty(), "warranty cannot be null");
        if (warranty.status() != WarrantyStatus.ACTIVE) {
            reasons.add("warranty is not ACTIVE: " + warranty.status());
            return new EligibilityResult(false, reasons, warranty.id().value(), null);
        }
        if (warranty.isExpiredAt(request.now(), request.meterValue())) {
            reasons.add("warranty validity ended (date/meter limit)");
            return new EligibilityResult(false, reasons, warranty.id().value(), null);
        }
        if (request.exclusionCode() != null) {
            for (WarrantyExclusion exclusion : warranty.exclusions()) {
                if (exclusion.code().equalsIgnoreCase(request.exclusionCode())) {
                    reasons.add("excluded by " + exclusion.code() + ": " + exclusion.description());
                    return new EligibilityResult(false, reasons, warranty.id().value(), null);
                }
            }
        }
        UUID target = request.componentAssetId() == null ? warranty.assetId() : request.componentAssetId();
        for (WarrantyCoverage coverage : warranty.coverages()) {
            if (!coverage.active()) {
                continue;
            }
            if (coverage.scope() == CoverageScope.ASSET && request.componentAssetId() == null) {
                reasons.add("covered by " + coverage.coverageCode());
                return new EligibilityResult(true, reasons, warranty.id().value(), coverage.id());
            }
            if (coverage.scope() == CoverageScope.COMPONENT && request.componentAssetId() != null
                    && (coverage.coveredAssetId() == null || coverage.coveredAssetId().equals(target))) {
                reasons.add("covered by " + coverage.coverageCode());
                return new EligibilityResult(true, reasons, warranty.id().value(), coverage.id());
            }
        }
        reasons.add("no active coverage matches");
        return new EligibilityResult(false, reasons, warranty.id().value(), null);
    }
}
