package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.domain.model.Invoice;
import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceStatus;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete invoice view with all details.
 */
public record InvoiceView(
        String invoiceId,
        String customerId,
        String customerName,
        String customerEmail,
        String invoiceNumber,
        String status,
        String statusDescription,
        String invoiceDate,
        String dueDate,
        List<InvoiceLineView> lines,
        String subtotal,
        String taxTotal,
        String discountTotal,
        String total,
        String paidAmount,
        String balance,
        String remainingBalance,
        String currencyCode,
        String customerNotes,
        String purchaseOrderNumber,
        List<PaymentView> payments,
        List<HistoryView> history,
        String pdfUrl,
        boolean overdue,
        int daysOverdue,
        String createdAt,
        String updatedAt
) {

    public static InvoiceView fromDomain(Invoice invoice, String customerName, String customerEmail) {
        List<InvoiceLineView> lineViews = invoice.getLines().stream()
            .map(InvoiceLineView::fromDomain)
            .collect(Collectors.toList());

        List<PaymentView> paymentViews = invoice.getPayments().stream()
            .map(PaymentView::fromDomain)
            .collect(Collectors.toList());

        boolean isOverdue = invoice.getStatus().isOutstanding() && 
            Instant.now().isAfter(invoice.getDueDate());
        long daysOverdue = isOverdue ? 
            java.time.temporal.ChronoUnit.DAYS.between(
                invoice.getDueDate(), Instant.now()
            ) : 0;

        return new InvoiceView(
            invoice.getId().toString(),
            invoice.getCustomerId().toString(),
            customerName,
            customerEmail,
            invoice.getInvoiceNumber(),
            invoice.getStatus().name(),
            invoice.getStatus().getDescription(),
            invoice.getInvoiceDate().toString(),
            invoice.getDueDate().toString(),
            lineViews,
            invoice.getSubtotal().getAmount().toPlainString(),
            invoice.getTaxTotal().getAmount().toPlainString(),
            invoice.getDiscountTotal().getAmount().toPlainString(),
            invoice.getTotal().getAmount().toPlainString(),
            invoice.getPaidAmount().getAmount().toPlainString(),
            invoice.getBalance().getAmount().toPlainString(),
            invoice.getRemainingBalance().getAmount().toPlainString(),
            invoice.getTotal().getCurrency().getCurrencyCode(),
            invoice.getCustomerNotes(),
            invoice.getPurchaseOrderNumber(),
            paymentViews,
            List.of(), // History would be populated separately
            null, // PDF URL would be generated
            isOverdue,
            (int) daysOverdue,
            invoice.getCreatedAt().toString(),
            invoice.getUpdatedAt().toString()
        );
    }

    public record InvoiceLineView(
            String productId,
            String description,
            int quantity,
            String unitPrice,
            String lineTotal,
            String taxAmount,
            String discountAmount,
            String currencyCode
    ) {
        public static InvoiceLineView fromDomain(Invoice.InvoiceLine line) {
            return new InvoiceLineView(
                line.getProductId() != null ? line.getProductId().toString() : null,
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice().getAmount().toPlainString(),
                line.getLineTotal().getAmount().toPlainString(),
                line.getTaxAmount().getAmount().toPlainString(),
                line.getDiscountAmount().getAmount().toPlainString(),
                line.getLineTotal().getCurrency().getCurrencyCode()
            );
        }
    }

    public record PaymentView(
            String transactionId,
            String amount,
            String method,
            String reference,
            String date,
            String currencyCode
    ) {
        public static PaymentView fromDomain(Invoice.Payment payment) {
            return new PaymentView(
                payment.getTransactionId(),
                payment.getAmount().getAmount().toPlainString(),
                payment.getMethod().name(),
                payment.getReference(),
                payment.getDate().toString(),
                payment.getAmount().getCurrency().getCurrencyCode()
            );
        }
    }

    public record HistoryView(
            String action,
            String fromStatus,
            String toStatus,
            String performedBy,
            String performedAt,
            String notes
    ) {}
}