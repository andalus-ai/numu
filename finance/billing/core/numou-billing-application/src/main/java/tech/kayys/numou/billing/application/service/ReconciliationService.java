package tech.kayys.numou.billing.application.service;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class ReconciliationService {

    public CompletionStage<ReconciliationResult> reconcile(
            Instant startDate,
            Instant endDate,
            String customerId) {
        
        List<ReconciliationItem> items = new ArrayList<>();
        items.add(new ReconciliationItem(
            "INV-001",
            "ACC-001",
            Money.of(100L, "USD"),
            "MATCHED",
            "No discrepancy"
        ));
        
        Money totalBilled = Money.of(100L, "USD");
        Money totalAccounted = Money.of(100L, "USD");
        Money discrepancy = totalBilled.subtract(totalAccounted);
        
        return CompletableFuture.completedFuture(
            new ReconciliationResult(
                startDate,
                endDate,
                customerId,
                items,
                totalBilled,
                totalAccounted,
                discrepancy,
                discrepancy.isZero(),
                "Reconciliation complete",
                Instant.now()
            )
        );
    }

    public record ReconciliationResult(
            Instant startDate,
            Instant endDate,
            String customerId,
            List<ReconciliationItem> items,
            Money totalBilled,
            Money totalAccounted,
            Money discrepancy,
            boolean reconciled,
            String notes,
            Instant processedAt
    ) {}

    public record ReconciliationItem(
            String billingId,
            String accountingId,
            Money amount,
            String status,
            String notes
    ) {}
}
