package tech.kayys.numou.support.application.api;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.support.domain.ticket.RequesterRole;

import java.util.Objects;

/**
 * Tenant-verified participant representation for a linked Party.
 * The resolver that creates this value must verify the Party's active
 * relationship to the tenant and the role it has in that tenant.
 */
public record SupportPartyContext(ParticipantId participantId, RequesterRole requesterRole) {
    public SupportPartyContext {
        Objects.requireNonNull(participantId, "participantId cannot be null");
        Objects.requireNonNull(requesterRole, "requesterRole cannot be null");
    }
}
