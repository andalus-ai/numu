package tech.kayys.syirkah.groceries.domain.repository;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.groceries.domain.identifier.ScaleId;
import tech.kayys.syirkah.groceries.domain.model.ScaleDevice;

import java.util.concurrent.CompletionStage;

/**
 * Repository for ScaleDevice aggregate.
 */
public interface ScaleDeviceRepository extends Repository<ScaleDevice, ScaleId> {

    CompletionStage<ScaleDevice> findBySerialNumber(String serialNumber);

    CompletionStage<java.util.List<ScaleDevice>> findByScaleType(ScaleDevice.ScaleType scaleType);

    CompletionStage<java.util.List<ScaleDevice>> findConnectedScales();
}
