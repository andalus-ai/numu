package tech.kayys.numou.restaurant.kernel.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record MenuItemId(UUID value) implements DomainId<UUID>, Serializable {

    public MenuItemId {
        Objects.requireNonNull(value, "MenuItemId value must not be null");
    }

    public static MenuItemId of(UUID value) {
        return new MenuItemId(value);
    }

    public static MenuItemId of(String value) {
        return new MenuItemId(UUID.fromString(value));
    }

    public static MenuItemId generate() {
        return new MenuItemId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
