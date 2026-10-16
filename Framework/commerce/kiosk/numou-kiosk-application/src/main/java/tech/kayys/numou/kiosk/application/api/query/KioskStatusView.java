package tech.kayys.syirkah.kiosk.application.api.query;

import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.valueobject.KioskMode;
import tech.kayys.syirkah.kiosk.domain.valueobject.KioskStatus;

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
