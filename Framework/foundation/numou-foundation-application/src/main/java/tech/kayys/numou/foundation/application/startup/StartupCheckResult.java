package tech.kayys.numou.foundation.application.startup;

import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of executing an individual startup check (config01.md §P4-04 #5).
 */
public record StartupCheckResult(
        String checkId,
        StartupCheckSeverity severity,
        boolean passed,
        String message,
        List<String> details
) {
    public StartupCheckResult {
        Objects.requireNonNull(checkId, "checkId cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        details = details != null ? List.copyOf(details) : List.of();
    }

    public static StartupCheckResult passed(String id, String message) {
        return new StartupCheckResult(id, StartupCheckSeverity.REQUIRED, true, message, List.of());
    }

    public static StartupCheckResult warning(String id, String message, List<String> details) {
        return new StartupCheckResult(id, StartupCheckSeverity.WARNING, false, message, details);
    }

    public static StartupCheckResult failed(String id, StartupCheckSeverity severity, String message) {
        return new StartupCheckResult(id, severity, false, message, List.of());
    }

    public static StartupCheckResult failed(String id, StartupCheckSeverity severity, String message, List<String> details) {
        return new StartupCheckResult(id, severity, false, message, details);
    }
}
