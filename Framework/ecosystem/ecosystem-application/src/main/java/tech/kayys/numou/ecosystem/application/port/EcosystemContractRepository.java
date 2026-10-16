package tech.kayys.syirkah.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.ecosystem.domain.identifier.CapabilityId;
import tech.kayys.syirkah.ecosystem.domain.identifier.EcosystemContractId;
import tech.kayys.syirkah.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.syirkah.ecosystem.domain.model.EcosystemContract;

import java.util.List;
import java.util.Optional;

/** Outbound port for ecosystem contracts. */
public interface EcosystemContractRepository {

    Uni<EcosystemContract> save(EcosystemContract contract);

    Uni<Optional<EcosystemContract>> findById(EcosystemContractId id);

    Uni<List<EcosystemContract>> findActiveForProvider(ParticipantId providerParticipantId);

    Uni<List<EcosystemContract>> findActiveForCapability(CapabilityId capabilityId);
}
