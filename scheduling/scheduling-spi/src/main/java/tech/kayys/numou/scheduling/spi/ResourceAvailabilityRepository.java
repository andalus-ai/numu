package tech.kayys.numou.scheduling.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.scheduling.domain.ResourceAvailability;
import tech.kayys.numou.scheduling.domain.ResourceAvailabilityId;

public interface ResourceAvailabilityRepository extends Repository<ResourceAvailability, ResourceAvailabilityId> {}
