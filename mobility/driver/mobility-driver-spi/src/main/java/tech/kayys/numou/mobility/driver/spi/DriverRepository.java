package tech.kayys.numou.mobility.driver.spi;

import tech.kayys.numou.aggregate.spi.VersionedRepository;
import tech.kayys.numou.mobility.driver.domain.Driver;
import tech.kayys.numou.mobility.kernel.domain.DriverId;

public interface DriverRepository extends VersionedRepository<Driver, DriverId> { }
