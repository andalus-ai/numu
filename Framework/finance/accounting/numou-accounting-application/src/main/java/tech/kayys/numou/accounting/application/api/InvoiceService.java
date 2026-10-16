package tech.kayys.syirkah.accounting.application.api;

import tech.kayys.syirkah.accounting.application.api.command.*;
import tech.kayys.syirkah.accounting.application.api.query.*;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;

import java.util.concurrent.CompletionStage;

/**
 * Comprehensive invoice management service.
 */
public interface InvoiceService {

    // ============ Write Operations ============

    /**
     * Creates a new invoice.
     */
    CompletionStage<InvoiceId> createInvoice(CreateInvoiceCommand command);

    /**
     * Sends an invoice to the customer.
     */
    CompletionStage<InvoiceId> sendInvoice(SendInvoiceCommand command);

    /**
     * Records a payment against an invoice.
     */
    CompletionStage<InvoiceId> recordPayment(RecordPaymentCommand command);

    /**
     * Records a payment failure for an invoice.
     */
    CompletionStage<InvoiceId> recordPaymentFailure(RecordPaymentFailureCommand command);

    /**
     * Issues a refund for an invoice.
     */
    CompletionStage<InvoiceId> refundInvoice(RefundInvoiceCommand command);

    /**
     * Writes off an invoice as uncollectable.
     */
    CompletionStage<InvoiceId> writeOffInvoice(WriteOffInvoiceCommand command);

    /**
     * Cancels an invoice.
     */
    CompletionStage<InvoiceId> cancelInvoice(CancelInvoiceCommand command);

    /**
     * Generates a PDF for an invoice.
     */
    CompletionStage<byte[]> generateInvoicePdf(GenerateInvoicePdfCommand command);

    // ============ Read Operations ============

    /**
     * Gets a complete invoice view.
     */
    CompletionStage<InvoiceView> getInvoice(GetInvoiceQuery query);

    /**
     * Gets invoice summary for a customer.
     */
    CompletionStage<InvoiceSummaryView> getInvoiceSummary(GetInvoiceSummaryQuery query);

    /**
     * Searches invoices with filters.
     */
    CompletionStage<InvoiceSearchResult> searchInvoices(SearchInvoicesQuery query);

    /**
     * Gets invoice statistics.
     */
    CompletionStage<InvoiceStatistics> getInvoiceStatistics(InvoiceStatisticsQuery query);
}