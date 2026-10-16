package tech.kayys.syirkah.construction.application.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.construction.application.profile.command.CreateConstructionProjectProfileCommand;
import tech.kayys.syirkah.construction.application.profile.handler.CreateConstructionProjectProfileHandler;
import tech.kayys.syirkah.construction.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.construction.domain.profile.ConstructionContractType;
import tech.kayys.syirkah.construction.domain.profile.ConstructionType;
import tech.kayys.syirkah.construction.domain.profile.DeliveryMethod;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CreateConstructionProjectProfileHandlerTest {
    private FakeConstructionProjectProfileRepository repository;
    private RecordingEventPublisher eventPublisher;
    private CreateConstructionProjectProfileHandler handler;

    @BeforeEach
    void setUp() {
        repository = new FakeConstructionProjectProfileRepository();
        eventPublisher = new RecordingEventPublisher();
        handler = new CreateConstructionProjectProfileHandler(repository, eventPublisher);
    }

    @Test
    void shouldCreateProfileSuccessfully() {
        var projectId = UUID.randomUUID();
        var command = new CreateConstructionProjectProfileCommand(
                projectId,
                ConstructionType.INFRASTRUCTURE,
                DeliveryMethod.EPC,
                ConstructionContractType.LUMP_SUM,
                "Bridge Construction"
        );

        var profile = handler.handle(command).await().indefinitely();

        assertThat(profile).isNotNull();
        assertThat(profile.projectId()).isEqualTo(projectId);
        assertThat(eventPublisher.published()).hasSize(1);
    }
}
