package tech.kayys.numou.foundation.application.startup;

import java.time.Instant;
import java.util.*;

/**
 * Default implementation of StartupValidator organizing checks by StartupCheckPhase.
 */
public final class DefaultStartupValidator implements StartupValidator {

    private final List<StartupCheck> checks;

    public DefaultStartupValidator(Collection<StartupCheck> checks) {
        this.checks = checks != null ? new ArrayList<>(checks) : new ArrayList<>();
    }

    @Override
    public StartupValidationReport validate(StartupValidationContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        // Sort checks deterministically by phase ordinal, then checkId
        List<StartupCheck> sorted = new ArrayList<>(checks);
        sorted.sort(Comparator.comparing(StartupCheck::phase).thenComparing(StartupCheck::id));

        List<StartupCheckResult> results = new ArrayList<>();
        for (StartupCheck check : sorted) {
            try {
                StartupCheckResult res = check.check(context);
                results.add(res != null ? res : StartupCheckResult.passed(check.id(), "Passed"));
            } catch (Exception e) {
                results.add(StartupCheckResult.failed(
                        check.id(),
                        StartupCheckSeverity.REQUIRED,
                        "Check threw unexpected exception: " + e.getMessage()
                ));
            }
        }

        return new StartupValidationReport(Instant.now(), results);
    }
}
