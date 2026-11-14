package tech.kayys.numou.crm.domain.model;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerPartyAssociationTest {

    @Test
    void createsAssociationUsingStronglyTypedIdentifiers() {
        CustomerId customerId = CustomerId.generate();
        PartyId partyId = PartyId.generate();

        CustomerPartyAssociation association =
                CustomerPartyAssociation.associate(customerId, partyId);

        assertEquals(customerId, association.customerId());
        assertEquals(partyId, association.partyId());
        assertNotNull(association.associatedAt());
    }

    @Test
    void rejectsMissingCustomerOrPartyIdentity() {
        PartyId partyId = PartyId.of(UUID.randomUUID());
        CustomerId customerId = CustomerId.generate();

        assertThrows(NullPointerException.class,
                () -> new CustomerPartyAssociation(null, partyId, java.time.Instant.now()));
        assertThrows(NullPointerException.class,
                () -> new CustomerPartyAssociation(customerId, null, java.time.Instant.now()));
    }

    @Test
    void rejectsMissingAssociationTimestamp() {
        assertThrows(NullPointerException.class,
                () -> new CustomerPartyAssociation(
                        CustomerId.generate(), PartyId.generate(), null));
    }
}
