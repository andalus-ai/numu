package tech.kayys.tax.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class TaxCalculationRequest {
    
    @NotBlank
    public String npwp;
    
    @NotNull
    public Integer taxYear;
    
    @NotBlank
    public String taxPeriod = "ANNUAL"; // MONTHLY, QUARTERLY, ANNUAL
    
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    public BigDecimal grossIncome;
    
    @NotNull
    @DecimalMin(value = "0.0")
    public BigDecimal deductibleExpenses;
    
    public String notes;
    
    public String calculatedBy;
    
    // Constructors
    public TaxCalculationRequest() {}
    
    public TaxCalculationRequest(String npwp, Integer taxYear, String taxPeriod, 
                               BigDecimal grossIncome, BigDecimal deductibleExpenses, 
                               String notes, String calculatedBy) {
        this.npwp = npwp;
        this.taxYear = taxYear;
        this.taxPeriod = taxPeriod;
        this.grossIncome = grossIncome;
        this.deductibleExpenses = deductibleExpenses;
        this.notes = notes;
        this.calculatedBy = calculatedBy;
    }
}
