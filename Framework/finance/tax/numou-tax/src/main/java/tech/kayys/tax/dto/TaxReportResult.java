package tech.kayys.tax.dto;

import tech.kayys.tax.entity.TaxReport;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TaxReportResult {
    
    public Long reportId;
    public String reportType;
    public Integer taxYear;
    public LocalDate filingDate;
    public TaxReport.ReportStatus status;
    public BigDecimal totalTaxAmount;
    public String acknowledgmentNumber;
    
    public TaxReportResult() {}
    
    public TaxReportResult(Long reportId, String reportType, Integer taxYear,
                         LocalDate filingDate, TaxReport.ReportStatus status,
                         BigDecimal totalTaxAmount, String acknowledgmentNumber) {
        this.reportId = reportId;
        this.reportType = reportType;
        this.taxYear = taxYear;
        this.filingDate = filingDate;
        this.status = status;
        this.totalTaxAmount = totalTaxAmount;
        this.acknowledgmentNumber = acknowledgmentNumber;
    }
}
