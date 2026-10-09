package tech.kayys.syirkah.plugin.loader;

import tech.kayys.syirkah.plugin.api.SyirkahPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads plugins using standard Java ServiceLoader mechanism.
 */
public class ServiceLoaderPluginLoader {

    public List<SyirkahPlugin> loadPlugins(ClassLoader classLoader) {
        List<SyirkahPlugin> result = new ArrayList<>();
        ServiceLoader<SyirkahPlugin> loader = ServiceLoader.load(SyirkahPlugin.class, classLoader);
        for (SyirkahPlugin plugin : loader) {
            result.add(plugin);
        }
        return result;
    }

    public List<SyirkahPlugin> loadPlugins() {
        return loadPlugins(Thread.currentThread().getContextClassLoader());
    }
}
