package tech.kayys.numou.kiosk.application.api.query;

import tech.kayys.numou.kiosk.domain.identifier.KioskId;
import tech.kayys.numou.kiosk.domain.identifier.KioskSessionId;

import java.time.Instant;
import java.util.List;

public record KioskSessionHistory(
        KioskSessionId sessionId,
        KioskId kioskId,
        List<String> actions,
        Instant startedAt,
        Instant endedAt
) {
}
