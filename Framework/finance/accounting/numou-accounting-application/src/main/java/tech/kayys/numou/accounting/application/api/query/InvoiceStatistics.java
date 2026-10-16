package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.domain.valueobject.InvoiceStatus;

import java.util.Map;

/**
 * Invoice statistics for reporting.
 */
public record InvoiceStatistics(
        int totalInvoices,
        int totalOpenInvoices,
        int totalOverdueInvoices,
        String totalRevenue,
        String totalOutstanding,
        String totalOverdue,
        Map<InvoiceStatus, Integer> statusCounts,
        String currencyCode,
        String periodStart,
        String periodEnd
) {}