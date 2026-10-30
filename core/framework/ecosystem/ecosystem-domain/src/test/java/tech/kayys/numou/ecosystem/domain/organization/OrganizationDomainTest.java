package tech.kayys.numou.ecosystem.domain.organization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Organization domain tests")
class OrganizationDomainTest {

    private static OrganizationType companyType() {
        return OrganizationType.of("numou", "company");
    }

    @Test
    @DisplayName("Registers organization profile using canonical Party identity")
    void registersAgainstPartyIdentity() {
        PartyId partyId = PartyId.generate();
        UUID tenantId = UUID.randomUUID();

        Organization organization = Organization.register(
                partyId, "PT Andalus Teknologi", "Andalus", companyType(), tenantId
        );

        assertEquals(partyId, organization.partyId());
        assertEquals("PT Andalus Teknologi", organization.legalName());
        assertEquals("Andalus", organization.displayName());
        assertEquals("numou:company", organization.type().qualifiedCode());
        assertEquals(tenantId, organization.tenantId());
        assertEquals(OrganizationStatus.DRAFT, organization.status());
        assertFalse(organization.isActive());
    }

    @Test
    @DisplayName("Supports external organizations without tenant ownership")
    void supportsExternalOrganization() {
        Organization organization = Organization.register(
                PartyId.generate(), "External Provider Ltd", "External Provider",
                OrganizationType.of("numou", "external-provider"), null
        );

        assertNull(organization.tenantId());
    }

    @Test
    @DisplayName("Activates, suspends, and resumes organization")
    void managesLifecycle() {
        Organization organization = Organization.register(
                PartyId.generate(), "PT Example", "Example", companyType(), null
        );

        organization.activate();
        assertEquals(OrganizationStatus.ACTIVE, organization.status());

        organization.suspend("Compliance review");
        assertEquals(OrganizationStatus.SUSPENDED, organization.status());

        organization.activate();
        assertEquals(OrganizationStatus.ACTIVE, organization.status());
    }

    @Test
    @DisplayName("Requires a reason when suspending or dissolving")
    void requiresLifecycleReasons() {
        Organization organization = Organization.register(
                PartyId.generate(), "PT Example", "Example", companyType(), null
        );
        organization.activate();

        assertThrows(BusinessRuleViolation.class, () -> organization.suspend(" "));
        assertThrows(BusinessRuleViolation.class, () -> organization.dissolve(null));
    }

    @Test
    @DisplayName("Dissolution is terminal and preserves identity")
    void dissolutionIsTerminal() {
        PartyId partyId = PartyId.generate();
        Organization organization = Organization.register(
                partyId, "PT Example", "Example", companyType(), null
        );

        organization.dissolve("Legal dissolution");
        assertEquals(partyId, organization.partyId());
        assertEquals(OrganizationStatus.DISSOLVED, organization.status());
        assertThrows(InvalidStateException.class, organization::activate);
        assertThrows(InvalidStateException.class,
                () -> organization.changeLegalName("New Name"));
    }

    @Test
    @DisplayName("Rejects blank names and incomplete classifications")
    void validatesRequiredValues() {
        assertThrows(BusinessRuleViolation.class, () -> Organization.register(
                PartyId.generate(), " ", "Example", companyType(), null
        ));
        assertThrows(IllegalArgumentException.class,
                () -> OrganizationType.of("numou", " "));
    }

    @Test
    @DisplayName("Allows new classification codes without changing the core enum")
    void supportsNamespacedClassification() {
        OrganizationType foundation = OrganizationType.of("indonesia", "yayasan");
        assertEquals("indonesia:yayasan", foundation.qualifiedCode());
    }
}
