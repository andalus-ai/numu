package tech.kayys.numou.history.graph.application;

import java.util.Objects;
import java.util.UUID;

/** Runs a full read-only reconciliation and count check for one staging generation. */
public final class HistoricalProjectionGenerationValidator implements HistoricalProjectionGenerationValidationPort {
    private final HistoricalProjectionReconciliationService reconciliation;
    private final GenerationTargetedHistoricalProjectionInspectionPort inspection;
    private final int pageSize;

    public HistoricalProjectionGenerationValidator(HistoricalProjectionReconciliationService reconciliation,
            GenerationTargetedHistoricalProjectionInspectionPort inspection, int pageSize) {
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize must be 1..200");
        this.pageSize = pageSize;
    }

    @Override
    public ValidationResult validate(UUID tenantId, UUID generationId) {
        try {
            var report = reconciliation.reconcileGeneration(tenantId, generationId, pageSize, false);
            long projected = 0;
            String cursor = null;
            while (true) {
                var page = inspection.nextPageInGeneration(tenantId, generationId, cursor, pageSize);
                if (page.isEmpty()) break;
                projected += page.size();
                cursor = page.get(page.size() - 1).relationshipId().toString();
                if (page.size() < pageSize) break;
            }
            return new ValidationResult(true, report.canonicalChecked(), projected,
                    report.missing(), report.stale(), report.divergent(), report.orphaned(),
                    "full canonical-to-generation reconciliation completed");
        } catch (Exception failure) {
            return new ValidationResult(false, 0, 0, 0, 0, 0, 0,
                    "generation validation could not complete: " + failure.getClass().getSimpleName());
        }
    }
}
