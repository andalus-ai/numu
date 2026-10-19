package tech.kayys.numou.billing.application.port;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Port for exporting billing data.
 */
public interface ExportPort {

    /**
     * Exports invoices to CSV.
     */
    CompletionStage<byte[]> exportInvoicesToCsv(ExportRequest request);

    /**
     * Exports invoices to Excel.
     */
    CompletionStage<byte[]> exportInvoicesToExcel(ExportRequest request);

    /**
     * Exports billing report to PDF.
     */
    CompletionStage<byte[]> exportBillingReportToPdf(ExportRequest request);

    /**
     * Export request record.
     */
    record ExportRequest(
        String format, // CSV, EXCEL, PDF
        String reportType, // INVOICES, USAGE, REVENUE
        Instant fromDate,
        Instant toDate,
        List<String> fields,
        String customerId,
        String status
    ) {}
}