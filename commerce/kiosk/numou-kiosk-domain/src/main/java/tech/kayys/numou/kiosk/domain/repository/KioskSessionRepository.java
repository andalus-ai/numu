package tech.kayys.numou.kiosk.domain.repository;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.kiosk.domain.identifier.KioskId;
import tech.kayys.numou.kiosk.domain.identifier.KioskSessionId;
import tech.kayys.numou.kiosk.domain.model.KioskSession;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface KioskSessionRepository extends Repository<KioskSession, KioskSessionId> {
    CompletionStage<List<KioskSession>> findByKioskId(KioskId kioskId);
    CompletionStage<List<KioskSession>> findActiveSessions(KioskId kioskId);
}
