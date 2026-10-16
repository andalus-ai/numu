package tech.kayys.syirkah.accounting.consolidation;

import java.util.Objects;

/**
 * Aggregate representing a corporate consolidation group.
 */
public record ConsolidationGroup(
        String groupId,
        String groupCode,
        String name,
        String reportingCurrency,
        String scope
) {
    public ConsolidationGroup {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(groupCode, "groupCode must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(reportingCurrency, "reportingCurrency must not be null");
        if (scope == null || scope.isBlank()) {
            scope = "DEFAULT";
        }
    }
}
