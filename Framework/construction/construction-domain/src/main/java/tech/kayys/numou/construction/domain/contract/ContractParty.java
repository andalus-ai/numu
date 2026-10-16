package tech.kayys.syirkah.construction.domain.contract;

import java.util.Objects;
import java.util.UUID;

public record ContractParty(UUID organizationRefId, String name, ContractPartyRole role) {
    public ContractParty {
        Objects.requireNonNull(organizationRefId, "Organization ref cannot be null");
        Objects.requireNonNull(name, "Party name cannot be blank");
        Objects.requireNonNull(role, "Role cannot be null");
    }
}
