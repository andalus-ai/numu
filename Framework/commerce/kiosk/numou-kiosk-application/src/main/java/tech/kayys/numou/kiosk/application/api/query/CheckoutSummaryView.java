package tech.kayys.syirkah.kiosk.application.api.query;

import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;

import java.math.BigDecimal;

public record CheckoutSummaryView(
        KioskSessionId sessionId,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currencyCode,
        boolean paymentCompleted
) {
}
