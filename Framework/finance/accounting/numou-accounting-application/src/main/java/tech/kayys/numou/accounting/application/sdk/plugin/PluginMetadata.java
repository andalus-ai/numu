package tech.kayys.syirkah.accounting.application.sdk.plugin;

/**
 * Metadata record for a PlatformPlugin – used by the marketplace and by
 * AI-based introspection to understand what a plugin provides.
 */
public record PluginMetadata(
        String id,
        String name,
        String version,
        String category,   // e.g. "compliance", "tax", "workflow"
        String vendor,
        String description
) {}
