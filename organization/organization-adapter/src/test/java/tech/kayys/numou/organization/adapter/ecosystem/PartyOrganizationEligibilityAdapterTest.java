package tech.kayys.numou.organization.adapter.ecosystem;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PartyOrganizationEligibilityAdapterTest {

    @Test
    void mapsActiveOrganizationPartyAsEligible() {
        PartyId id = PartyId.generate();
        var adapter = adapter(id, Optional.of(Party.rehydrate(id, PartyKind.ORGANIZATION, PartyStatus.ACTIVE)));

        var eligibility = adapter.findEligibility(id).toCompletableFuture().join();

        assertTrue(eligibility.exists());
        assertTrue(eligibility.active());
        assertTrue(eligibility.organizationKind());
        assertTrue(eligibility.canRepresentOrganization());
    }

    @Test
    void mapsPersonPartyAsIneligibleOrganization() {
        PartyId id = PartyId.generate();
        var adapter = adapter(id, Optional.of(Party.rehydrate(id, PartyKind.PERSON, PartyStatus.ACTIVE)));

        var eligibility = adapter.findEligibility(id).toCompletableFuture().join();

        assertTrue(eligibility.exists());
        assertTrue(eligibility.active());
        assertFalse(eligibility.organizationKind());
        assertFalse(eligibility.canRepresentOrganization());
    }

    @Test
    void mapsInactiveOrganizationPartyAsIneligible() {
        PartyId id = PartyId.generate();
        var adapter = adapter(id, Optional.of(Party.rehydrate(id, PartyKind.ORGANIZATION, PartyStatus.INACTIVE)));

        var eligibility = adapter.findEligibility(id).toCompletableFuture().join();

        assertTrue(eligibility.exists());
        assertFalse(eligibility.active());
        assertTrue(eligibility.organizationKind());
        assertFalse(eligibility.canRepresentOrganization());
    }

    @Test
    void mapsMissingPartyAsIneligible() {
        PartyId id = PartyId.generate();
        var eligibility = adapter(id, Optional.empty()).findEligibility(id).toCompletableFuture().join();

        assertFalse(eligibility.exists());
        assertFalse(eligibility.active());
        assertFalse(eligibility.organizationKind());
        assertFalse(eligibility.canRepresentOrganization());
    }

    private static PartyOrganizationEligibilityAdapter adapter(PartyId requested, Optional<Party> result) {
        PartyLookupPort lookup = id -> {
            assertEquals(requested, id);
            return Uni.createFrom().item(result);
        };
        return new PartyOrganizationEligibilityAdapter(lookup);
    }
}
