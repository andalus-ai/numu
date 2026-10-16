package tech.kayys.syirkah.kiosk.domain.repository;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.model.KioskDevice;
import tech.kayys.syirkah.kiosk.domain.valueobject.KioskStatus;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface KioskDeviceRepository extends Repository<KioskDevice, KioskId> {
    CompletionStage<List<KioskDevice>> findByStatus(KioskStatus status);
    CompletionStage<List<KioskDevice>> findByStoreId(String storeId);
}
