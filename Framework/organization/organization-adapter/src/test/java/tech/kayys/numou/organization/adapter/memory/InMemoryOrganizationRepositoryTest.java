package tech.kayys.syirkah.organization.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.organization.domain.Organization;
import tech.kayys.syirkah.organization.domain.OrganizationId;
import tech.kayys.syirkah.organization.domain.OrganizationUnit;
import tech.kayys.syirkah.organization.domain.OrganizationUnitId;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrganizationRepositoryTest {

    @Test
    void organization_saveAndFind() throws ExecutionException, InterruptedException {
        InMemoryOrganizationRepository repo = new InMemoryOrganizationRepository();
        Organization org = Organization.create(
                OrganizationId.generate(), TenantId.generate(),
                "Test Org", null, null);

        repo.save(org).toCompletableFuture().get();
        Optional<Organization> found = repo.findById(org.getId()).toCompletableFuture().get();

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Org");
    }

    @Test
    void organizationUnit_saveAndFind() throws ExecutionException, InterruptedException {
        InMemoryOrganizationUnitRepository repo = new InMemoryOrganizationUnitRepository();
        OrganizationUnit unit = OrganizationUnit.create(
                OrganizationUnitId.generate(), OrganizationId.generate(),
                null, "Engineering", "ENG");

        repo.save(unit).toCompletableFuture().get();
        Optional<OrganizationUnit> found = repo.findById(unit.getId()).toCompletableFuture().get();

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Engineering");
    }
}
