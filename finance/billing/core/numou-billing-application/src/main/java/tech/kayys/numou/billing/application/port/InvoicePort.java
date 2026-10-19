package tech.kayys.numou.billing.application.port;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * Port for invoice generation.
 */
public interface InvoicePort {

    /**
     * Generates an invoice for billing.
     */
    CompletionStage<String> generateInvoice(
        String customerId,
        Money amount,
        String currencyCode,
        String description
    );

    /**
     * Gets invoice details.
     */
    CompletionStage<InvoiceDetails> getInvoice(String invoiceId);

    record InvoiceDetails(
        String invoiceId,
        String customerId,
        Money amount,
        String currencyCode,
        String status,
        Instant createdAt
    ) {}
}