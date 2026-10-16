package tech.kayys.numou.plugin.loader;

import tech.kayys.numou.plugin.api.NumouPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads plugins using standard Java ServiceLoader mechanism.
 */
public class ServiceLoaderPluginLoader {

    public List<NumouPlugin> loadPlugins(ClassLoader classLoader) {
        List<NumouPlugin> result = new ArrayList<>();
        ServiceLoader<NumouPlugin> loader = ServiceLoader.load(NumouPlugin.class, classLoader);
        for (NumouPlugin plugin : loader) {
            result.add(plugin);
        }
        return result;
    }

    public List<NumouPlugin> loadPlugins() {
        return loadPlugins(Thread.currentThread().getContextClassLoader());
    }
}
