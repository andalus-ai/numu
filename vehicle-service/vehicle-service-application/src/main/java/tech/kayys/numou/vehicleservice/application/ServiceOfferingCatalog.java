package tech.kayys.numou.vehicleservice.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.vehicleservice.domain.ServiceOffering;
import tech.kayys.numou.vehicleservice.domain.ServiceOfferingId;

import java.util.Optional;

/** Public application port for retrieving the authoritative service catalogue entry. */
public interface ServiceOfferingCatalog {
    Uni<Optional<ServiceOffering>> find(String tenantId, ServiceOfferingId offeringId);
}
