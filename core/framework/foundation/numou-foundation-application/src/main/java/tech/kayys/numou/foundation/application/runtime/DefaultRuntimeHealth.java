package tech.kayys.numou.foundation.application.runtime;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Default implementation of RuntimeHealth.
 */
public final class DefaultRuntimeHealth implements RuntimeHealth {

    private final BooleanSupplier healthSupplier;

    public DefaultRuntimeHealth(BooleanSupplier healthSupplier) {
        this.healthSupplier = Objects.requireNonNull(healthSupplier, "healthSupplier cannot be null");
    }

    @Override
    public boolean isHealthy() {
        return healthSupplier.getAsBoolean();
    }

    @Override
    public String status() {
        return isHealthy() ? "UP" : "DOWN";
    }
}
