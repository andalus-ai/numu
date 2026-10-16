package tech.kayys.syirkah.accounting.application.sdk.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry holding all active {@link PlatformPlugin} instances.
 * Plugins are registered during platform bootstrap and remain immutable at runtime.
 */
public final class PluginRegistry {

    private final List<PlatformPlugin> plugins = new ArrayList<>();

    public void register(PlatformPlugin plugin) {
        plugins.add(plugin);
    }

    public List<PlatformPlugin> all() {
        return Collections.unmodifiableList(plugins);
    }

    public boolean isEmpty() {
        return plugins.isEmpty();
    }
}
