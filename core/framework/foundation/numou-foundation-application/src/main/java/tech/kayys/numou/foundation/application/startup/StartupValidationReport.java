package tech.kayys.numou.foundation.application.startup;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated report of all executed startup checks (config01.md §P4-04 #28).
 */
public record StartupValidationReport(
        Instant validatedAt,
        List<StartupCheckResult> results
) {
    public StartupValidationReport {
        Objects.requireNonNull(validatedAt, "validatedAt cannot be null");
        results = results != null ? List.copyOf(results) : List.of();
    }

    public boolean passed() {
        return results.stream().noneMatch(r ->
                !r.passed() && r.severity() == StartupCheckSeverity.REQUIRED
        );
    }

    public List<StartupCheckResult> failures() {
        return results.stream()
                .filter(r -> !r.passed() && r.severity() == StartupCheckSeverity.REQUIRED)
                .toList();
    }

    public List<StartupCheckResult> warnings() {
        return results.stream()
                .filter(r -> !r.passed() && r.severity() == StartupCheckSeverity.WARNING)
                .toList();
    }
}
