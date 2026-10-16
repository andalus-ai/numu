package tech.kayys.syirkah.kiosk.domain.repository;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;
import tech.kayys.syirkah.kiosk.domain.model.KioskSession;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface KioskSessionRepository extends Repository<KioskSession, KioskSessionId> {
    CompletionStage<List<KioskSession>> findByKioskId(KioskId kioskId);
    CompletionStage<List<KioskSession>> findActiveSessions(KioskId kioskId);
}
