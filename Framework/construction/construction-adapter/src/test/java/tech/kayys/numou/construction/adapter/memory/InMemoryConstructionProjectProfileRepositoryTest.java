package tech.kayys.syirkah.construction.adapter.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.construction.domain.profile.*;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class InMemoryConstructionProjectProfileRepositoryTest {
    private InMemoryConstructionProjectProfileRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryConstructionProjectProfileRepository();
    }

    @Test
    void shouldSaveAndFindProfile() {
        var projectId = UUID.randomUUID();
        var profile = ConstructionProjectProfile.create(
                projectId,
                ConstructionType.RESIDENTIAL,
                DeliveryMethod.DESIGN_BID_BUILD,
                ConstructionContractType.UNIT_PRICE,
                "Residential Complex"
        );

        var saved = repository.save(profile).toCompletableFuture().join();
        assertThat(saved).isNotNull();

        var foundById = repository.findById(profile.id()).toCompletableFuture().join();
        assertThat(foundById).isPresent();
        assertThat(foundById.get().projectId()).isEqualTo(projectId);

        var foundByProject = repository.findByProjectId(projectId).toCompletableFuture().join();
        assertThat(foundByProject).isPresent();
        assertThat(foundByProject.get().id()).isEqualTo(profile.id());

        var exists = repository.existsById(profile.id()).toCompletableFuture().join();
        assertThat(exists).isTrue();

        repository.delete(profile).toCompletableFuture().join();
        var afterDelete = repository.findById(profile.id()).toCompletableFuture().join();
        assertThat(afterDelete).isEmpty();
    }
}
