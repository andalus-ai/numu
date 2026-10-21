package tech.kayys.numou.mobility.driver.adapter.memory;

import tech.kayys.numou.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.numou.mobility.driver.domain.Driver;
import tech.kayys.numou.mobility.driver.spi.DriverRepository;
import tech.kayys.numou.mobility.kernel.domain.DriverId;

public class InMemoryDriverRepository extends InMemoryDocumentRepository<Driver, DriverId, Driver.Snapshot> implements DriverRepository {
    public InMemoryDriverRepository() {
        super(Driver::snapshot, Driver::restore);
    }
}
