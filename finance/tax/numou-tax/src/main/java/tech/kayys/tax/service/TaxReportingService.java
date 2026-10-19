package tech.kayys.tax.service;

import tech.kayys.tax.entity.Company;
import tech.kayys.tax.entity.TaxCalculation;
import tech.kayys.tax.entity.TaxReport;
import tech.kayys.tax.dto.TaxReportRequest;
import tech.kayys.tax.dto.TaxReportResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class TaxReportingService {
    
    private static final Logger LOG = Logger.getLogger(TaxReportingService.class.getName());
    
    @Transactional
    public TaxReportResult generateSptTahunan(TaxReportRequest request) {
        LOG.info("Generating SPT Tahunan for NPWP: " + request.npwp);
        
        Company company = Company.findByNpwp(request.npwp);
        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }
        
        // Get all tax calculations for the year
        List<TaxCalculation> calculations = TaxCalculation.list(
            "company = ?1 and taxYear = ?2", company, request.taxYear
        );
        
        if (calculations.isEmpty()) {
            throw new IllegalArgumentException("No tax calculations found for the specified year");
        }
        
        // Calculate totals
        BigDecimal totalTaxAmount = calculations.stream()
            .map(calc -> calc.totalTaxLiability)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Create report
        TaxReport report = new TaxReport();
        report.company = company;
        report.reportType = "SPT_TAHUNAN";
        report.taxYear = request.taxYear;
        report.filingDate = LocalDate.now();
        report.dueDate = LocalDate.of(request.taxYear + 1, 3, 31);
        report.totalTaxAmount = totalTaxAmount;
        report.submittedBy = request.submittedBy;
        report.notes = request.notes;
        report.persist();
        
        LOG.info("SPT Tahunan generated successfully for company: " + company.npwp);
        
        return new TaxReportResult(report.id, report.reportType, report.taxYear, 
            report.filingDate, report.status, report.totalTaxAmount, report.acknowledgmentNumber);
    }
    
    @Transactional
    public void submitReport(Long reportId, String acknowledgmentNumber) {
        TaxReport report = TaxReport.findById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("Report not found");
        }
        
        report.status = TaxReport.ReportStatus.SUBMITTED;
        report.submissionDate = java.time.LocalDateTime.now();
        report.acknowledgmentNumber = acknowledgmentNumber;
        report.persist();
        
        LOG.info("Report submitted successfully: " + reportId);
    }
    
    public List<TaxReport> getCompanyReports(String npwp) {
        Company company = Company.findByNpwp(npwp);
        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }
        
        return TaxReport.list("company = ?1 order by taxYear desc", company);
    }
}
