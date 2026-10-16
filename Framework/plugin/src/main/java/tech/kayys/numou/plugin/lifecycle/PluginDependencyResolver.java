package tech.kayys.numou.plugin.lifecycle;

import tech.kayys.numou.plugin.api.PluginDependency;
import tech.kayys.numou.plugin.api.PluginException;
import tech.kayys.numou.plugin.api.NumouPlugin;

import java.util.*;

/**
 * Resolves plugin dependencies and determines initialization/startup execution order using topological sorting.
 */
public class PluginDependencyResolver {

    public List<NumouPlugin> resolveOrder(Collection<NumouPlugin> plugins) {
        Map<String, NumouPlugin> pluginMap = new HashMap<>();
        for (NumouPlugin p : plugins) {
            pluginMap.put(p.id(), p);
        }

        // Validate dependencies
        for (NumouPlugin p : plugins) {
            for (PluginDependency dep : p.manifest().dependencies()) {
                if (!pluginMap.containsKey(dep.pluginId())) {
                    if (!dep.optional()) {
                        throw new PluginException("Unresolved required dependency: plugin '" + p.id() +
                                "' requires '" + dep.pluginId() + "' (" + dep.versionRequirement() + ")");
                    }
                }
            }
        }

        // Topological sort (Kahn's algorithm)
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();

        for (String id : pluginMap.keySet()) {
            inDegree.put(id, 0);
            graph.put(id, new ArrayList<>());
        }

        for (NumouPlugin p : plugins) {
            for (PluginDependency dep : p.manifest().dependencies()) {
                if (pluginMap.containsKey(dep.pluginId())) {
                    // dep.pluginId -> p.id (dep must be initialized before p)
                    graph.get(dep.pluginId()).add(p.id());
                    inDegree.put(p.id(), inDegree.get(p.id()) + 1);
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<NumouPlugin> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            sorted.add(pluginMap.get(u));

            for (String v : graph.get(u)) {
                int newDegree = inDegree.get(v) - 1;
                inDegree.put(v, newDegree);
                if (newDegree == 0) {
                    queue.add(v);
                }
            }
        }

        if (sorted.size() != plugins.size()) {
            throw new PluginException("Circular dependency detected among plugins");
        }

        return Collections.unmodifiableList(sorted);
    }
}
