package tech.kayys.numou.crm.application.participant;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;

import java.util.concurrent.CompletionStage;

/**
 * Reads the Party capability without making CRM the owner of party master data.
 */
public interface ParticipantPort {

    CompletionStage<Boolean> exists(ParticipantId participantId);
}
