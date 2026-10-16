package tech.kayys.syirkah.accounting.application.sdk.plugin;

/**
 * Generalized plugin contract.
 *
 * <p>Current built-in examples: IFRS, PSAK, AAOIFI.
 * Third parties can deliver: Tax, Workflow, Notification, AI, Treasury, …
 *
 * <p>Discovered via {@link java.util.ServiceLoader} or registered programmatically.
 */
public interface PlatformPlugin {

    /** Rich metadata for marketplace catalogues. */
    PluginMetadata metadata();

    /**
     * Called once during platform startup.
     * Plugins register rules, projections and statements via the context.
     */
    void initialize(PluginContext context);
}
