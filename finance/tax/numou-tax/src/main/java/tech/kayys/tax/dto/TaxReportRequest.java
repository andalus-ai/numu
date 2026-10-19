package tech.kayys.tax.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TaxReportRequest {
    
    @NotBlank
    public String npwp;
    
    @NotNull
    public Integer taxYear;
    
    @NotBlank
    public String reportType;
    
    public String submittedBy;
    public String notes;
    
    // Constructors
    public TaxReportRequest() {}
    
    public TaxReportRequest(String npwp, Integer taxYear, String reportType, 
                          String submittedBy, String notes) {
        this.npwp = npwp;
        this.taxYear = taxYear;
        this.reportType = reportType;
        this.submittedBy = submittedBy;
        this.notes = notes;
    }
}
