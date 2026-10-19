package tech.kayys.numou.support.application.crm;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Optional read-only CRM integration; Support remains usable if CRM is unavailable.
 */
public interface CrmPartyPort {

    CompletionStage<Optional<PartySummary>> findParty(ParticipantId participantId);

    CompletionStage<Optional<PartySummary>> findByEmail(String normalizedEmail);

    record PartySummary(ParticipantId participantId, String displayName, String participantType,
                        boolean active) {
    }
}
