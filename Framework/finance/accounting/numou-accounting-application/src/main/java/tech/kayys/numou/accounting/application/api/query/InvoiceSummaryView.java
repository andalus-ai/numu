package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.domain.model.Invoice;

import java.util.List;

/**
 * Summary view for a customer's invoices.
 */
public record InvoiceSummaryView(
        String customerId,
        String customerName,
        int totalInvoices,
        int openInvoices,
        int overdueInvoices,
        String totalAmount,
        String openAmount,
        String overdueAmount,
        String currencyCode,
        List<InvoiceBrief> recentInvoices
) {

    public record InvoiceBrief(
            String invoiceId,
            String invoiceNumber,
            String status,
            String date,
            String total,
            String balance
    ) {
        public static InvoiceBrief fromDomain(Invoice invoice) {
            return new InvoiceBrief(
                invoice.getId().toString(),
                invoice.getInvoiceNumber(),
                invoice.getStatus().name(),
                invoice.getInvoiceDate().toString(),
                invoice.getTotal().getAmount().toPlainString(),
                invoice.getRemainingBalance().getAmount().toPlainString()
            );
        }
    }
}