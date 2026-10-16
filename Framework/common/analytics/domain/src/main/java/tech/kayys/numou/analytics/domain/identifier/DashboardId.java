package tech.kayys.syirkah.analytics.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Dashboard identifier.
 */
public record DashboardId(UUID value) implements DomainId<UUID>, Serializable {

    public DashboardId {
        Objects.requireNonNull(value, "DashboardId value cannot be null");
    }

    public static DashboardId of(UUID value) {
        return new DashboardId(value);
    }

    public static DashboardId generate() {
        return new DashboardId(UUID.randomUUID());
    }

    public static DashboardId fromString(String value) {
        return new DashboardId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "DashboardId{" + value + "}";
    }
}