package tech.kayys.numou.finance.treasury.application.api.query;

import tech.kayys.numou.finance.treasury.domain.identifier.DrawerSessionId;
import java.math.BigDecimal;
import java.time.Instant;

public record ZReportView(
        DrawerSessionId sessionId,
        String registerId,
        String cashierId,
        String currencyCode,
        BigDecimal openingFloat,
        BigDecimal cashSales,
        BigDecimal cashRefunds,
        BigDecimal cashIn,
        BigDecimal cashOut,
        BigDecimal expectedCash,
        BigDecimal actualCash,
        BigDecimal discrepancy,
        Instant openedAt,
        Instant closedAt
) {
}
