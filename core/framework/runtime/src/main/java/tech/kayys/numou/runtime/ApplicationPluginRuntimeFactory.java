package tech.kayys.numou.runtime;

import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-scoped adapter from resolved capability providers to trusted
 * existing NumouPlugin implementations.
 *
 * <p>Only plugins explicitly registered by trusted bootstrap code can be
 * activated. This class deliberately does not use RuntimePluginManager's
 * global start operation.</p>
 */
public final class ApplicationPluginRuntimeFactory implements CapabilityRuntimeFactory {
    private final Map<String, Registration> registrations;
    private final SharedServices sharedServices = new SharedServices();

    private ApplicationPluginRuntimeFactory(Map<String, Registration> registrations) {
        this.registrations = Collections.unmodifiableMap(new TreeMap<>(registrations));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CapabilityRuntime create(CapabilityProvider provider) {
        Objects.requireNonNull(provider, "Resolved capability provider is required");
        Registration registration = registrations.get(provider.providerId());
        if (registration == null) {
            throw new ApplicationRuntimeException(
                    "No application plugin registered for provider " + provider.providerId());
        }
        if (!registration.capabilityId().equals(provider.capabilityId())) {
            throw new ApplicationRuntimeException("Capability mismatch for provider " + provider.providerId()
                    + ": registered " + registration.capabilityId() + " but resolved " + provider.capabilityId());
        }
        if (!registration.contractVersion().equals(provider.contractVersion())) {
            throw new ApplicationRuntimeException("Contract version mismatch for provider " + provider.providerId()
                    + ": registered " + registration.contractVersion() + " but resolved " + provider.contractVersion());
        }
        return new PluginCapabilityRuntime(registration.plugin(),
                new ApplicationPluginContext(registration.plugin().id(), registration.properties(), sharedServices));
    }

    public Set<String> providerIds() {
        return registrations.keySet();
    }

    public static final class Builder {
        private final Map<String, Registration> registrations = new TreeMap<>();
        private final Set<String> pluginIds = new java.util.HashSet<>();

        /**
         * Register a trusted plugin for one capability provider. A plugin instance
         * may be assigned only once per application; this avoids duplicate lifecycle
         * invocation when one plugin advertises multiple capabilities. A future
         * grouped-provider adapter can represent that lifecycle explicitly.
         */
        public Builder register(String providerId, String capabilityId, String contractVersion,
                                NumouPlugin plugin, Map<String, String> properties) {
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalArgumentException("Provider ID is required");
            }
            if (capabilityId == null || capabilityId.isBlank()) {
                throw new IllegalArgumentException("Capability ID is required");
            }
            if (contractVersion == null || contractVersion.isBlank()) {
                throw new IllegalArgumentException("Contract version is required");
            }
            Objects.requireNonNull(plugin, "Plugin is required");
            boolean providesCapability = plugin.capabilities().stream()
                    .map(Capability::name)
                    .anyMatch(name -> name.equalsIgnoreCase(capabilityId));
            if (!providesCapability) {
                throw new IllegalArgumentException("Plugin " + plugin.id()
                        + " does not advertise capability " + capabilityId);
            }
            if (!pluginIds.add(plugin.id())) {
                throw new IllegalArgumentException("Plugin instance is already registered in this application: "
                        + plugin.id());
            }
            Registration registration = new Registration(providerId, capabilityId, contractVersion,
                    plugin, properties == null ? Map.of() : Map.copyOf(properties));
            if (registrations.putIfAbsent(providerId, registration) != null) {
                pluginIds.remove(plugin.id());
                throw new IllegalArgumentException("Duplicate application provider registration: " + providerId);
            }
            return this;
        }

        public ApplicationPluginRuntimeFactory build() {
            return new ApplicationPluginRuntimeFactory(registrations);
        }
    }

    private record Registration(String providerId, String capabilityId, String contractVersion,
                                NumouPlugin plugin, Map<String, String> properties) {
    }

    private static final class PluginCapabilityRuntime implements CapabilityRuntime {
        private final NumouPlugin plugin;
        private final PluginContext context;
        private boolean initializationAttempted;
        private boolean stopped;

        private PluginCapabilityRuntime(NumouPlugin plugin, PluginContext context) {
            this.plugin = plugin;
            this.context = context;
        }

        @Override
        public String providerId() {
            return plugin.id();
        }

        @Override
        public void start() throws Exception {
            if (initializationAttempted) {
                throw new IllegalStateException("Plugin runtime is single-use: " + plugin.id());
            }
            initializationAttempted = true;
            plugin.initialize(context);
            plugin.start();
        }

        @Override
        public void stop() throws Exception {
            if (stopped || !initializationAttempted) {
                stopped = true;
                return;
            }
            stopped = true;
            plugin.stop();
        }
    }

    private static final class SharedServices {
        private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

        <T> T get(Class<T> type) {
            Object value = services.get(type);
            return value == null ? null : type.cast(value);
        }

        <T> void register(Class<T> type, T instance) {
            Objects.requireNonNull(type, "Service type is required");
            Objects.requireNonNull(instance, "Service instance is required");
            if (!type.isInstance(instance)) {
                throw new IllegalArgumentException("Service instance does not implement " + type.getName());
            }
            Object existing = services.putIfAbsent(type, instance);
            if (existing != null && existing != instance) {
                throw new IllegalStateException("A different application service is already registered for "
                        + type.getName());
            }
        }
    }

    private static final class ApplicationPluginContext implements PluginContext {
        private final String pluginId;
        private final Map<String, String> properties;
        private final SharedServices sharedServices;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        private ApplicationPluginContext(String pluginId, Map<String, String> properties,
                                          SharedServices sharedServices) {
            this.pluginId = pluginId;
            this.properties = properties;
            this.sharedServices = sharedServices;
        }

        @Override
        public String pluginId() {
            return pluginId;
        }

        @Override
        public java.util.Optional<String> getProperty(String key) {
            return key == null ? java.util.Optional.empty()
                    : java.util.Optional.ofNullable(properties.get(key));
        }

        @Override
        public <T> java.util.Optional<T> getService(Class<T> serviceType) {
            return serviceType == null ? java.util.Optional.empty()
                    : java.util.Optional.ofNullable(sharedServices.get(serviceType));
        }

        @Override
        public <T> void registerService(Class<T> serviceType, T instance) {
            sharedServices.register(serviceType, instance);
        }

        @Override
        public Map<String, Object> attributes() {
            return Map.copyOf(attributes);
        }

        @Override
        public void setAttribute(String key, Object value) {
            if (key == null) {
                return;
            }
            if (value == null) {
                attributes.remove(key);
            } else {
                attributes.put(key, value);
            }
        }
    }
}
