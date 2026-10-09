package tech.kayys.syirkah.plugin;

import tech.kayys.syirkah.plugin.api.Capability;
import tech.kayys.syirkah.plugin.api.SyirkahPlugin;
import tech.kayys.syirkah.plugin.lifecycle.PluginDependencyResolver;
import tech.kayys.syirkah.plugin.lifecycle.PluginLifecycleManager;
import tech.kayys.syirkah.plugin.loader.ServiceLoaderPluginLoader;
import tech.kayys.syirkah.plugin.registry.CapabilityRegistry;
import tech.kayys.syirkah.plugin.registry.DefaultCapabilityRegistry;
import tech.kayys.syirkah.plugin.registry.DefaultPluginRegistry;
import tech.kayys.syirkah.plugin.registry.PluginRegistry;
import tech.kayys.syirkah.plugin.router.CapabilityRouter;

import java.util.List;
import java.util.Optional;

/**
 * Primary runtime entry point and manager for the Syirkah Plugin Platform.
 */
public class RuntimePluginManager {

    private final PluginRegistry pluginRegistry;
    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityRouter capabilityRouter;
    private final PluginDependencyResolver dependencyResolver;
    private final PluginLifecycleManager lifecycleManager;
    private final ServiceLoaderPluginLoader pluginLoader;

    public RuntimePluginManager() {
        this.pluginRegistry = new DefaultPluginRegistry();
        this.capabilityRegistry = new DefaultCapabilityRegistry();
        this.capabilityRouter = new CapabilityRouter(this.capabilityRegistry);
        this.dependencyResolver = new PluginDependencyResolver();
        this.lifecycleManager = new PluginLifecycleManager(
                this.pluginRegistry,
                this.capabilityRegistry,
                this.dependencyResolver
        );
        this.pluginLoader = new ServiceLoaderPluginLoader();
    }

    /**
     * Auto-discover plugins on the classpath via ServiceLoader and register them.
     */
    public void discoverAndRegister() {
        List<SyirkahPlugin> discovered = pluginLoader.loadPlugins();
        for (SyirkahPlugin plugin : discovered) {
            pluginRegistry.register(plugin);
        }
    }

    /**
     * Register a plugin programmatically.
     */
    public void register(SyirkahPlugin plugin) {
        pluginRegistry.register(plugin);
    }

    /**
     * Bootstraps all registered plugins (dependency order -> initialize -> start).
     */
    public void start() {
        lifecycleManager.initializeAll();
        lifecycleManager.startAll();
    }

    /**
     * Shuts down all plugins in reverse dependency order.
     */
    public void stop() {
        lifecycleManager.stopAll();
    }

    public PluginRegistry plugins() {
        return pluginRegistry;
    }

    public CapabilityRegistry capabilities() {
        return capabilityRegistry;
    }

    public CapabilityRouter router() {
        return capabilityRouter;
    }

    public Optional<SyirkahPlugin> findProvider(Capability capability) {
        return capabilityRouter.route(capability);
    }

    public Optional<SyirkahPlugin> findProvider(String capabilityName) {
        return capabilityRouter.route(capabilityName);
    }
}
