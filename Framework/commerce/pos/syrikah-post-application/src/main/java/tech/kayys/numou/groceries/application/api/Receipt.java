package tech.kayys.syirkah.groceries.application.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Receipt(
        UUID receiptId,
        String transactionNumber,
        Instant timestamp,
        List<ReceiptLine> items,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        String paymentMethod,
        String cashierId
) {}

record ReceiptLine(
        String productId,
        String productName,
        Double quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        boolean isWeightBased
) {}
