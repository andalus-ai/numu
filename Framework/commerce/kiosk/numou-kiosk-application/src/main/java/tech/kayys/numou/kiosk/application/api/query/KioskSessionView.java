package tech.kayys.syirkah.kiosk.application.api.query;

import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;
import tech.kayys.syirkah.kiosk.domain.valueobject.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record KioskSessionView(
        KioskSessionId sessionId,
        KioskId kioskId,
        SessionStatus status,
        String customerId,
        String currencyCode,
        String language,
        long itemCount,
        BigDecimal totalAmount,
        Instant startedAt,
        Instant endedAt
) {
}
