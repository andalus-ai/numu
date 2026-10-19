package tech.kayys.numou.foundation.application.startup;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thrown when one or more REQUIRED startup checks fail (config01.md §P4-04 #29).
 */
public class StartupValidationException extends RuntimeException {

    private final StartupValidationReport report;

    public StartupValidationException(StartupValidationReport report) {
        super(buildMessage(Objects.requireNonNull(report, "report cannot be null")));
        this.report = report;
    }

    public StartupValidationReport report() {
        return report;
    }

    private static String buildMessage(StartupValidationReport report) {
        String failures = report.failures().stream()
                .map(f -> "  - [" + f.checkId() + "] " + f.message())
                .collect(Collectors.joining("\n"));
        return "Application startup validation failed:\n" + failures;
    }
}
