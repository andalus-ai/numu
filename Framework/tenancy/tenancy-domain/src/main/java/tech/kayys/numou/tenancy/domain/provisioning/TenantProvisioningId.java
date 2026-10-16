package tech.kayys.syirkah.tenancy.domain.provisioning;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record TenantProvisioningId(UUID value) implements DomainId<UUID>, Serializable {

    public TenantProvisioningId {
        Objects.requireNonNull(value, "TenantProvisioningId must not be null");
    }

    public static TenantProvisioningId newId() {
        return new TenantProvisioningId(UUID.randomUUID());
    }

    @Override public String toString() { return value.toString(); }
}
