package tech.kayys.syirkah.commerce.configuration.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Identifier for a configuration session. */
public record ProductConfigurationId(UUID value) implements DomainId<UUID> {

    public ProductConfigurationId {
        if (value == null) {
            throw new IllegalArgumentException("Configuration id cannot be null");
        }
    }

    public static ProductConfigurationId generate() {
        return new ProductConfigurationId(UUID.randomUUID());
    }

    /** Alias matching product02.md naming. */
    public static ProductConfigurationId newId() {
        return generate();
    }
}
