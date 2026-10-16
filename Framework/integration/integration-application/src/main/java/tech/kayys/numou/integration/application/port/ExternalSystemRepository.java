package tech.kayys.numou.integration.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.integration.domain.identifier.ExternalSystemId;
import tech.kayys.numou.integration.domain.model.ExternalSystem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for external system registrations. */
public interface ExternalSystemRepository {

    Uni<ExternalSystem> save(ExternalSystem externalSystem);

    Uni<Optional<ExternalSystem>> findById(ExternalSystemId id);

    Uni<List<ExternalSystem>> findByParticipant(UUID participantId);
}
