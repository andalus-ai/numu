package tech.kayys.syirkah.ecosystem.domain.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Party & PartyRelationship Tests")
class PartyRelationshipTest {

    @Test
    @DisplayName("Should successfully establish a valid relationship between distinct parties")
    void shouldEstablishRelationship() {
        PartyId source = PartyId.of(UUID.randomUUID());
        PartyId target = PartyId.of(UUID.randomUUID());
        LocalDate today = LocalDate.now();

        PartyRelationship rel = PartyRelationship.establish(
                PartyRelationshipId.generate(),
                source,
                target,
                PartyRelationshipType.CUSTOMER_OF,
                today,
                today.plusYears(1)
        );

        assertNotNull(rel);
        assertEquals(source, rel.sourcePartyId());
        assertEquals(target, rel.targetPartyId());
        assertEquals(PartyRelationshipStatus.ACTIVE, rel.status());
        assertTrue(rel.isCurrentlyEffective(today));
        assertTrue(rel.isCurrentlyEffective(today.plusMonths(6)));
        assertFalse(rel.isCurrentlyEffective(today.plusYears(2)));
    }

    @Test
    @DisplayName("Should reject self-relationships")
    void shouldRejectSelfRelationship() {
        PartyId same = PartyId.of(UUID.randomUUID());
        LocalDate today = LocalDate.now();

        assertThrows(BusinessRuleViolation.class, () ->
                PartyRelationship.establish(
                        PartyRelationshipId.generate(),
                        same,
                        same,
                        PartyRelationshipType.PARTNER_OF,
                        today,
                        today.plusYears(1)
                ));
    }

    @Test
    @DisplayName("Should terminate relationship properly")
    void shouldTerminateRelationship() {
        PartyId source = PartyId.of(UUID.randomUUID());
        PartyId target = PartyId.of(UUID.randomUUID());
        LocalDate today = LocalDate.now();

        PartyRelationship rel = PartyRelationship.establish(
                PartyRelationshipId.generate(),
                source,
                target,
                PartyRelationshipType.SUPPLIER_OF,
                today,
                null
        );

        assertTrue(rel.isCurrentlyEffective(today));
        rel.terminate(today);

        assertEquals(PartyRelationshipStatus.TERMINATED, rel.status());
        assertFalse(rel.isCurrentlyEffective(today));
    }
}
