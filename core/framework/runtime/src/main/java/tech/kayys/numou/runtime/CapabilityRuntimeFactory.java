package tech.kayys.numou.runtime;

import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

/**
 * Resolves an already-approved provider descriptor to a runtime implementation.
 *
 * <p>The host deliberately does not load arbitrary classes or execute manifest
 * supplied code. The factory is supplied by the trusted application composition root.</p>
 */
@FunctionalInterface
public interface CapabilityRuntimeFactory {
    CapabilityRuntime create(CapabilityProvider provider);
}
