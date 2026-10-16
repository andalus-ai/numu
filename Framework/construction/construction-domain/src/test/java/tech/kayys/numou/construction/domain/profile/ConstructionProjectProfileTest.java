package tech.kayys.syirkah.construction.domain.profile;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConstructionProjectProfileTest {
    @Test
    void shouldCreateProfileAndRaiseEvent() {
        var projectId = UUID.randomUUID();
        var profile = ConstructionProjectProfile.create(
                projectId,
                ConstructionType.COMMERCIAL_BUILDING,
                DeliveryMethod.DESIGN_AND_BUILD,
                ConstructionContractType.LUMP_SUM,
                "Bandung Office Tower"
        );

        assertThat(profile.projectId()).isEqualTo(projectId);
        assertThat(profile.constructionType()).isEqualTo(ConstructionType.COMMERCIAL_BUILDING);
        assertThat(profile.deliveryMethod()).isEqualTo(DeliveryMethod.DESIGN_AND_BUILD);
        assertThat(profile.contractType()).isEqualTo(ConstructionContractType.LUMP_SUM);
        assertThat(profile.description()).isEqualTo("Bandung Office Tower");

        var events = profile.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("construction.project-profile-created");
    }
}
