package tech.kayys.syirkah.organization.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.organization.domain.event.OrganizationCreated;
import tech.kayys.syirkah.organization.domain.event.OrganizationDissolved;
import tech.kayys.syirkah.organization.domain.event.OrganizationReactivated;
import tech.kayys.syirkah.organization.domain.event.OrganizationSuspended;
import tech.kayys.syirkah.organization.domain.event.OrganizationUnitCreated;
import tech.kayys.syirkah.organization.domain.event.OrganizationUnitDissolved;

import static org.assertj.core.api.Assertions.*;

class OrganizationTest {

    private static final TenantId TENANT = TenantId.generate();

    @Test
    void create_raisesOrganizationCreatedEvent() {
        Organization org = Organization.create(
                OrganizationId.generate(), TENANT,
                "Engineering", "PT Engineering", "REG-001");

        assertThat(org.isActive()).isTrue();
        assertThat(org.pullDomainEvents())
                .hasSize(1)
                .first().isInstanceOf(OrganizationCreated.class);
    }

    @Test
    void suspend_activeOrganization_transitionsToSuspended() {
        Organization org = buildActive();
        org.suspend();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        assertThat(org.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(OrganizationSuspended.class);
    }

    @Test
    void suspend_alreadySuspended_throwsException() {
        Organization org = buildActive();
        org.suspend();
        org.pullDomainEvents();
        assertThatThrownBy(org::suspend)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reactivate_suspendedOrganization_transitionsToActive() {
        Organization org = buildActive();
        org.suspend();
        org.pullDomainEvents();
        org.reactivate();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(org.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(OrganizationReactivated.class);
    }

    @Test
    void dissolve_raisesOrganizationDissolvedEvent() {
        Organization org = buildActive();
        org.dissolve();
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.DISSOLVED);
        assertThat(org.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(OrganizationDissolved.class);
    }

    @Test
    void dissolve_alreadyDissolved_isIdempotent() {
        Organization org = buildActive();
        org.dissolve();
        org.pullDomainEvents();
        org.dissolve();
        assertThat(org.pullDomainEvents()).isEmpty();
    }

    @Test
    void createUnit_raisesOrganizationUnitCreatedEvent() {
        OrganizationId orgId = OrganizationId.generate();
        OrganizationUnit unit = OrganizationUnit.create(
                OrganizationUnitId.generate(), orgId, null, "Engineering", "ENG");

        assertThat(unit.isRoot()).isTrue();
        assertThat(unit.isActive()).isTrue();
        assertThat(unit.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(OrganizationUnitCreated.class);
    }

    @Test
    void dissolveUnit_raisesOrganizationUnitDissolvedEvent() {
        OrganizationId orgId = OrganizationId.generate();
        OrganizationUnit unit = OrganizationUnit.create(
                OrganizationUnitId.generate(), orgId, null, "HR", "HR");
        unit.pullDomainEvents();
        unit.dissolve();
        assertThat(unit.getStatus()).isEqualTo(OrganizationUnitStatus.DISSOLVED);
        assertThat(unit.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(OrganizationUnitDissolved.class);
    }

    private Organization buildActive() {
        Organization org = Organization.create(
                OrganizationId.generate(), TENANT,
                "Acme Corp", null, null);
        org.pullDomainEvents();
        return org;
    }
}
