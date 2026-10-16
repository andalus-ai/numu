package tech.kayys.syirkah.accounting.application.api.query;

import java.util.List;

/**
 * Search results for invoices with pagination.
 */
public record InvoiceSearchResult(
        List<InvoiceView> invoices,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static InvoiceSearchResult of(
            List<InvoiceView> invoices,
            long totalCount,
            int page,
            int size) {
        int totalPages = (int) Math.ceil((double) totalCount / size);
        return new InvoiceSearchResult(
            invoices,
            totalCount,
            page,
            size,
            totalPages,
            page < totalPages - 1,
            page > 0
        );
    }
}