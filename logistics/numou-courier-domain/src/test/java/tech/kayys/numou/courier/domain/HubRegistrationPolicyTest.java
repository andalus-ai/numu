package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnit;
import tech.kayys.numou.organization.domain.OrganizationUnitId;
import tech.kayys.numou.organization.domain.OrganizationUnitKind;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HubRegistrationPolicyTest {
    private final OrganizationId organizationId = OrganizationId.generate();

    private OrganizationUnit unit(OrganizationId orgId, OrganizationUnitKind kind) {
        return OrganizationUnit.create(OrganizationUnitId.generate(), orgId, null, kind,
                "Unit", "UNIT-1");
    }

    @Test
    void acceptsActiveBranchInTheRequestedOrganization() {
        assertThatCode(() -> HubRegistrationPolicy.requireEligibleBranch(
                organizationId, unit(organizationId, OrganizationUnitKind.BRANCH)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnitFromAnotherOrganization() {
        assertThatThrownBy(() -> HubRegistrationPolicy.requireEligibleBranch(
                organizationId, unit(OrganizationId.generate(), OrganizationUnitKind.BRANCH)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonBranchUnits() {
        assertThatThrownBy(() -> HubRegistrationPolicy.requireEligibleBranch(
                organizationId, unit(organizationId, OrganizationUnitKind.DEPARTMENT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDissolvedBranches() {
        OrganizationUnit branch = unit(organizationId, OrganizationUnitKind.BRANCH);
        branch.dissolve();

        assertThatThrownBy(() -> HubRegistrationPolicy.requireEligibleBranch(organizationId, branch))
                .isInstanceOf(IllegalStateException.class);
    }
}
