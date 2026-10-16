package tech.kayys.syirkah.project.domain.commercial;

import java.util.Objects;
import java.util.UUID;

/**
 * A reference to an external party on a contract.
 *
 * Commercial only needs the reference plus the role - it does not
 * model the party itself (that belongs to the party/CRM context).
 */
public record ContractParty(
        UUID partyId,
        ContractPartyRole role,
        String legalName
) {

    public ContractParty {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(role, "role cannot be null");

        if (legalName == null || legalName.isBlank()) {
            throw new IllegalArgumentException(
                    "legalName cannot be blank"
            );
        }
    }
}