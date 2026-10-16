package tech.kayys.syirkah.accounting.application.sdk.module;

/**
 * The primary extension point for third-party financial modules
 * (Payroll, HR, Manufacturing, CRM, Treasury, …).
 *
 * <p>Modules are discovered via {@link java.util.ServiceLoader} or registered
 * programmatically through {@link tech.kayys.syirkah.accounting.application.sdk.bootstrap.AccountingBootstrap}.
 */
public interface FinancialModule {

    /** Unique short code, e.g. {@code "payroll"}, {@code "inventory"}. */
    String code();

    /** Human-readable name. */
    String name();

    /** Rich metadata for marketplace and runtime introspection. */
    default ModuleMetadata metadata() {
        return new ModuleMetadata(code(), name(), "1.0.0", name() + " module", "community");
    }

    /**
     * Called once during platform bootstrap.
     * Modules register their commands, queries and projections via the context.
     */
    void register(ModuleContext context);
}
