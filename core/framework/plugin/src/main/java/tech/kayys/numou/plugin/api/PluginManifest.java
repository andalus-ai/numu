package tech.kayys.numou.plugin.api;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Manifest describing metadata, capabilities, and dependencies of a plugin.
 */
public record PluginManifest(
        String id,
        String name,
        String version,
        String description,
        String vendor,
        Set<Capability> capabilities,
        List<PluginDependency> dependencies
) {
    public PluginManifest {
        capabilities = capabilities == null ? Collections.emptySet() : Set.copyOf(capabilities);
        dependencies = dependencies == null ? Collections.emptyList() : List.copyOf(dependencies);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String name;
        private String version = "1.0.0";
        private String description = "";
        private String vendor = "community";
        private Set<Capability> capabilities = Set.of();
        private List<PluginDependency> dependencies = List.of();

        public Builder(String id) {
            this.id = id;
            this.name = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder vendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder capabilities(Set<Capability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder dependencies(List<PluginDependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public PluginManifest build() {
            return new PluginManifest(id, name, version, description, vendor, capabilities, dependencies);
        }
    }
}
