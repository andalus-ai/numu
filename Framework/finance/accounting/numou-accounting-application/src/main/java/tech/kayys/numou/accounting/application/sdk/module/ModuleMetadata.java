package tech.kayys.syirkah.accounting.application.sdk.module;

/**
 * Descriptive metadata for a FinancialModule.
 * Used by runtime introspection, marketplace catalogues and AI agents.
 */
public record ModuleMetadata(
        String code,
        String name,
        String version,
        String description,
        String vendor
) {}
