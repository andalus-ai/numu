package tech.kayys.numou.restaurant.kernel.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record MenuId(UUID value) implements DomainId<UUID>, Serializable {

    public MenuId {
        Objects.requireNonNull(value, "MenuId value must not be null");
    }

    public static MenuId of(UUID value) {
        return new MenuId(value);
    }

    public static MenuId of(String value) {
        return new MenuId(UUID.fromString(value));
    }

    public static MenuId generate() {
        return new MenuId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
