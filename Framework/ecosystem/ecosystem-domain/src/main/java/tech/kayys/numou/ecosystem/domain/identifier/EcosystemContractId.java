package tech.kayys.syirkah.ecosystem.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Identifies an ecosystem contract - the agreed commercial and
 * operational terms under which a provider supplies a capability.
 */
public record EcosystemContractId(UUID value) implements DomainId<UUID>, Serializable {

    public EcosystemContractId {
        Objects.requireNonNull(value, "EcosystemContractId value cannot be null");
    }

    public static EcosystemContractId of(UUID value) {
        return new EcosystemContractId(value);
    }

    public static EcosystemContractId generate() {
        return new EcosystemContractId(UUID.randomUUID());
    }

    public static EcosystemContractId fromString(String value) {
        return new EcosystemContractId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "EcosystemContractId{" + value + "}";
    }
}
