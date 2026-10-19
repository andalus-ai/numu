package tech.kayys.numou.foundation.application.startup;

/**
 * Executes startup checks across deterministic phases (config01.md §P4-04 #30).
 */
public interface StartupValidator {

    StartupValidationReport validate(StartupValidationContext context);
}
