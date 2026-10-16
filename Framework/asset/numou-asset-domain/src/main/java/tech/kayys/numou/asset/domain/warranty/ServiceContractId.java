package tech.kayys.numou.asset.domain.warranty;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record ServiceContractId(UUID value) implements DomainId<UUID>, Serializable {
        public ServiceContractId {
        Objects.requireNonNull(value, "ServiceContractId value cannot be null");
    }
    public static ServiceContractId of(UUID value) { return new ServiceContractId(value); }
    public static ServiceContractId generate() { return new ServiceContractId(UUID.randomUUID()); }
    public static ServiceContractId fromString(String value) { return new ServiceContractId(UUID.fromString(value)); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
