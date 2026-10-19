package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.identifier.RelationshipId;
import tech.kayys.numou.ecosystem.domain.model.ParticipantRelationship;

import java.util.List;
import java.util.Optional;

/** Outbound port for participant relationships. */
public interface ParticipantRelationshipRepository {

    Uni<ParticipantRelationship> save(ParticipantRelationship relationship);

    Uni<Optional<ParticipantRelationship>> findById(RelationshipId id);

    Uni<List<ParticipantRelationship>> findOutgoing(ParticipantId sourceParticipantId);

    Uni<List<ParticipantRelationship>> findIncoming(ParticipantId targetParticipantId);
}
