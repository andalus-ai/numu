package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.identifier.CapabilityId;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.model.ProviderCapability;

import java.util.List;

/**
 * Outbound port for provider capability claims.
 *
 * findByCapability is the query that makes provider-neutral fulfilment
 * possible: callers ask for capability, not for a concrete fleet or 3PL.
 */
public interface ProviderCapabilityRepository {

    Uni<ProviderCapability> save(ProviderCapability providerCapability);

    Uni<List<ProviderCapability>> findByParticipant(ParticipantId participantId);

    Uni<List<ProviderCapability>> findActiveByCapability(CapabilityId capabilityId);
}
