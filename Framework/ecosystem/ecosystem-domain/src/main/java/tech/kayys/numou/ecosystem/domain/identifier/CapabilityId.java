package tech.kayys.syirkah.ecosystem.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Identifies a capability in the capability catalog - for example
 * {@code logistics.transportation}, {@code commerce.retail.pos},
 * {@code finance.accounting}.
 *
 * <p>Capabilities are the platform's extension seam: a participant can
 * consume one without consuming all of Numou, and can provide one
 * without running Numou software. See {@code base00.md} §14.
 */
public record CapabilityId(UUID value) implements DomainId<UUID>, Serializable {

    public CapabilityId {
        Objects.requireNonNull(value, "CapabilityId value cannot be null");
    }

    public static CapabilityId of(UUID value) {
        return new CapabilityId(value);
    }

    public static CapabilityId generate() {
        return new CapabilityId(UUID.randomUUID());
    }

    public static CapabilityId fromString(String value) {
        return new CapabilityId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "CapabilityId{" + value + "}";
    }
}
