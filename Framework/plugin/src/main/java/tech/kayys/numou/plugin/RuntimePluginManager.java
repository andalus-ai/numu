package tech.kayys.numou.plugin;

import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.lifecycle.PluginDependencyResolver;
import tech.kayys.numou.plugin.lifecycle.PluginLifecycleManager;
import tech.kayys.numou.plugin.loader.ServiceLoaderPluginLoader;
import tech.kayys.numou.plugin.registry.CapabilityRegistry;
import tech.kayys.numou.plugin.registry.DefaultCapabilityRegistry;
import tech.kayys.numou.plugin.registry.DefaultPluginRegistry;
import tech.kayys.numou.plugin.registry.PluginRegistry;
import tech.kayys.numou.plugin.router.CapabilityRouter;

import java.util.List;
import java.util.Optional;

/**
 * Primary runtime entry point and manager for the Numou Plugin Platform.
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
        List<NumouPlugin> discovered = pluginLoader.loadPlugins();
        for (NumouPlugin plugin : discovered) {
            pluginRegistry.register(plugin);
        }
    }

    /**
     * Register a plugin programmatically.
     */
    public void register(NumouPlugin plugin) {
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

    public Optional<NumouPlugin> findProvider(Capability capability) {
        return capabilityRouter.route(capability);
    }

    public Optional<NumouPlugin> findProvider(String capabilityName) {
        return capabilityRouter.route(capabilityName);
    }
}
