package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a partner (syarik / shahibul mal / mudharib). Ordered for
 * deterministic tie-breaking.
 */
public record PartnerId(UUID value) implements DomainId<UUID>, Comparable<PartnerId>, Serializable {
    public PartnerId {
        Objects.requireNonNull(value, "PartnerId value must not be null");
    }

    public static PartnerId generate() {
        return new PartnerId(UUID.randomUUID());
    }

    public static PartnerId of(String value) {
        return new PartnerId(UUID.fromString(value));
    }

    @Override
    public int compareTo(PartnerId other) {
        return value.compareTo(other.value);
    }
}
