package tech.kayys.numou.accounting.application.spi;

import tech.kayys.numou.accounting.application.api.command.CreateInvoiceCommand;
import tech.kayys.numou.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.numou.accounting.application.api.command.RecordPaymentCommand;
import tech.kayys.numou.accounting.domain.identifier.InvoiceId;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;

import java.util.concurrent.CompletionStage;

/**
 * Public API for accounting commands.
 */
public interface AccountingCommandService {

    /**
     * Creates a new invoice.
     */
    CompletionStage<InvoiceId> createInvoice(CreateInvoiceCommand command);

    /**
     * Records a payment against an invoice.
     */
    CompletionStage<InvoiceId> recordPayment(RecordPaymentCommand command);

    /**
     * Posts a journal entry.
     */
    CompletionStage<JournalEntryId> postJournalEntry(PostJournalEntryCommand command);

    /**
     * Processes automated invoice generation for subscriptions.
     */
    CompletionStage<Integer> processRecurringInvoices();
}