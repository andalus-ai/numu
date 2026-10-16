package tech.kayys.syirkah.ecosystem.application.api.command;

import java.util.UUID;

/**
 * Result of registering a participant.
 *
 * @param participantId the new participant id
 * @param status        its lifecycle status - always PENDING on registration
 */
public record RegisterParticipantResult(UUID participantId, String status) {
}
