package tech.kayys.numou.crm.application.account;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;

public record CreateAccountCommand(ParticipantId participantId, String name) {
}
