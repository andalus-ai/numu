package tech.kayys.numou.ecosystem.domain.party;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import static org.junit.jupiter.api.Assertions.*;

class PartyTest {

    @Test
    void registersPersonAndOrganizationAsCanonicalKinds() {
        Party person = Party.register(PartyId.generate(), PartyKind.PERSON);
        Party organization = Party.register(PartyId.generate(), PartyKind.ORGANIZATION);

        assertEquals(PartyKind.PERSON, person.kind());
        assertEquals(PartyKind.ORGANIZATION, organization.kind());
        assertEquals(PartyStatus.ACTIVE, person.status());
        assertEquals(PartyStatus.ACTIVE, organization.status());
        assertNotEquals(person.getId(), organization.getId());
        assertEquals(1, person.pullDomainEvents().size());
    }

    @Test
    void unknownIsNotAValidPersistedPartyKind() {
        assertThrows(BusinessRuleViolation.class,
                () -> Party.register(PartyId.generate(), PartyKind.UNKNOWN));
    }

    @Test
    void deactivationAndReactivationAreExplicitLifecycleTransitions() {
        Party party = Party.register(PartyId.generate(), PartyKind.PERSON);

        party.deactivate();
        assertEquals(PartyStatus.INACTIVE, party.status());
        assertFalse(party.isActive());

        party.reactivate();
        assertEquals(PartyStatus.ACTIVE, party.status());
        assertTrue(party.isActive());
    }

    @Test
    void repeatedLifecycleTransitionsAreRejected() {
        Party party = Party.register(PartyId.generate(), PartyKind.ORGANIZATION);
        party.deactivate();

        assertThrows(RuntimeException.class, party::deactivate);
        party.reactivate();
        assertThrows(RuntimeException.class, party::reactivate);
    }
}
