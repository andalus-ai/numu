package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceStatus;

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