package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.identifier.CapabilityId;
import tech.kayys.numou.ecosystem.domain.model.Capability;

import java.util.Optional;

/** Outbound port for the capability catalog. */
public interface CapabilityRepository {

    Uni<Capability> save(Capability capability);

    Uni<Optional<Capability>> findById(CapabilityId id);

    Uni<Optional<Capability>> findByCode(String code);
}
