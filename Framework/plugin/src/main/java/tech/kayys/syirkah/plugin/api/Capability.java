package tech.kayys.syirkah.plugin.api;

import java.util.Objects;

/**
 * Declares a capability advertised or consumed by a plugin or module.
 * E.g., "PAYROLL", "TAX_INDONESIA", "COFFEE_RECIPE", "CONSTRUCTION_PROGRESS".
 */
public record Capability(String name, String version) {

    public Capability {
        Objects.requireNonNull(name, "Capability name must not be null");
        if (version == null) {
            version = "1.0.0";
        }
    }

    public static Capability of(String name) {
        return new Capability(name, "1.0.0");
    }

    public static Capability of(String name, String version) {
        return new Capability(name, version);
    }
}
