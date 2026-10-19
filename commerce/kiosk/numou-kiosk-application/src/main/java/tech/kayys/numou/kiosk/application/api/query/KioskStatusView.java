package tech.kayys.numou.kiosk.application.api.query;

import tech.kayys.numou.kiosk.domain.identifier.KioskId;
import tech.kayys.numou.kiosk.domain.valueobject.KioskMode;
import tech.kayys.numou.kiosk.domain.valueobject.KioskStatus;

public record KioskStatusView(
        KioskId kioskId,
        String deviceName,
        String model,
        String location,
        String storeId,
        KioskMode mode,
        KioskStatus status,
        boolean cashAccepted,
        boolean cardAccepted,
        boolean mobilePaymentAccepted
) {
}
