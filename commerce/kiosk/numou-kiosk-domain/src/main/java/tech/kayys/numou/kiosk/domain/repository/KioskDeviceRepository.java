package tech.kayys.numou.kiosk.domain.repository;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.kiosk.domain.identifier.KioskId;
import tech.kayys.numou.kiosk.domain.model.KioskDevice;
import tech.kayys.numou.kiosk.domain.valueobject.KioskStatus;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface KioskDeviceRepository extends Repository<KioskDevice, KioskId> {
    CompletionStage<List<KioskDevice>> findByStatus(KioskStatus status);
    CompletionStage<List<KioskDevice>> findByStoreId(String storeId);
}
