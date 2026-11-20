package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.time.Instant;
import java.util.Objects;

/**
 * Explicit relationship between a tenant-owned Customer role and a canonical
 * Ecosystem Party identity. Tenant ownership is supplied by the repository
 * execution context; it is deliberately not accepted from this value object.
 */
public record CustomerPartyAssociation(
        CustomerId customerId,
        PartyId partyId,
        Instant associatedAt) {

    public CustomerPartyAssociation {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(associatedAt, "associatedAt cannot be null");
    }

    public static CustomerPartyAssociation associate(CustomerId customerId, PartyId partyId) {
        return new CustomerPartyAssociation(customerId, partyId, Instant.now());
    }
}
