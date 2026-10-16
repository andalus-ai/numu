package tech.kayys.syirkah.accounting.application.port;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Port for transaction context.
 * Accounting context calls this to get transaction details.
 */
public interface TransactionPort {

    /**
     * Gets transaction details by ID.
     */
    CompletionStage<TransactionDetails> getTransaction(String transactionId);

    /**
     * Gets transactions for reconciliation.
     */
    CompletionStage<List<TransactionDetails>> getTransactionsForReconciliation(
        Instant fromDate,
        Instant toDate,
        String merchantId
    );

    /**
     * Transaction details record.
     */
    record TransactionDetails(
        String transactionId,
        String reference,
        String orderId,
        String customerId,
        Money amount,
        String currencyCode,
        String status,
        String paymentMethod,
        String processorTransactionId,
        Instant createdAt,
        Instant completedAt,
        String refundReference
    ) {}
}