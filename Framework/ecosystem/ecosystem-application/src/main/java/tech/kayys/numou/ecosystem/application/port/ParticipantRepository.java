package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.model.Participant;

import java.util.Optional;

/**
 * Outbound port for participant persistence. Implemented by infrastructure
 * adapters (JPA, in-memory test doubles, ...) - never by the domain.
 */
public interface ParticipantRepository {

    Uni<Participant> save(Participant participant);

    Uni<Optional<Participant>> findById(ParticipantId id);

    Uni<Optional<Participant>> findByCode(String code);

    Uni<Boolean> existsByCode(String code);
}
