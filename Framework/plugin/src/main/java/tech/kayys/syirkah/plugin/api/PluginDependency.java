package tech.kayys.syirkah.plugin.api;

/**
 * Declares a dependency required by a plugin.
 */
public record PluginDependency(
        String pluginId,
        String versionRequirement,
        boolean optional
) {
    public static PluginDependency required(String pluginId, String versionRequirement) {
        return new PluginDependency(pluginId, versionRequirement, false);
    }

    public static PluginDependency optional(String pluginId, String versionRequirement) {
        return new PluginDependency(pluginId, versionRequirement, true);
    }
}
