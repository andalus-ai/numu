package tech.kayys.numou.foundation.application.startup;

/**
 * Fundamental extension point for pre-flight startup checks (config01.md §P4-04 #3, #25).
 */
public interface StartupCheck {

    String id();

    default String description() {
        return id();
    }

    StartupCheckPhase phase();

    StartupCheckResult check(StartupValidationContext context);
}
