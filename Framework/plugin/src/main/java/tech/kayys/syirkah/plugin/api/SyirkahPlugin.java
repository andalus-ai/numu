package tech.kayys.syirkah.plugin.api;

import java.util.Set;

/**
 * Fundamental lifecycle and extensibility contract for plugins in Syirkah.
 * Adapted from Andalus Plugin architecture for the Syirkah Developer Platform.
 */
public interface SyirkahPlugin {

    /**
     * Unique identifier of the plugin, e.g. "tech.kayys.plugin.payroll".
     */
    String id();

    /**
     * Human-readable plugin name.
     */
    default String name() {
        return manifest().name();
    }

    /**
     * Plugin version.
     */
    default String version() {
        return manifest().version();
    }

    /**
     * Manifest describing capabilities and dependencies.
     */
    PluginManifest manifest();

    /**
     * Current lifecycle state of the plugin.
     */
    PluginState state();

    /**
     * Capabilities provided by this plugin.
     */
    default Set<Capability> capabilities() {
        return manifest().capabilities();
    }

    /**
     * ClassLoader that loaded this plugin.
     */
    default ClassLoader classLoader() {
        return getClass().getClassLoader();
    }

    /**
     * Lifecycle: initialize with platform context.
     */
    default void initialize(PluginContext context) throws Exception {
    }

    /**
     * Lifecycle: start plugin activities.
     */
    default void start() throws Exception {
    }

    /**
     * Lifecycle: stop plugin and release resources.
     */
    default void stop() throws Exception {
    }
}
