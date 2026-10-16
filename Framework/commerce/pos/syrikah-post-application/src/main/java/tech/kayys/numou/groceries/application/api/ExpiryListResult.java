package tech.kayys.syirkah.groceries.application.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExpiryListResult(
        List<ExpiringProduct> products,
        int totalCount,
        int daysThreshold
) {}

record ExpiringProduct(
        UUID productId,
        String productName,
        String batchNumber,
        Instant expiryDate,
        int quantityRemaining,
        int daysUntilExpiry
) {}
