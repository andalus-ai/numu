package tech.kayys.tax.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class TaxCalculationResult {
    
    public Long calculationId;
    public String companyName;
    public String npwp;
    public Integer taxYear;
    public String taxPeriod;
    public BigDecimal grossIncome;
    public BigDecimal deductibleExpenses;
    public BigDecimal taxableIncome;
    public BigDecimal taxRate;
    public BigDecimal corporateTaxAmount;
    public BigDecimal pphFinalAmount;
    public BigDecimal vatAmount;
    public BigDecimal withholdingTaxAmount;
    public BigDecimal penaltyAmount;
    public BigDecimal interestAmount;
    public BigDecimal totalTaxLiability;
    public Boolean isSmallBusiness;
    public BigDecimal effectiveRate;
    public LocalDate dueDate;
    
    public TaxCalculationResult() {}
    
    public TaxCalculationResult(Long calculationId, String companyName, String npwp, 
                             Integer taxYear, String taxPeriod, BigDecimal grossIncome, 
                             BigDecimal deductibleExpenses, BigDecimal taxableIncome, 
                             BigDecimal taxRate, BigDecimal corporateTaxAmount,
                             BigDecimal pphFinalAmount, BigDecimal vatAmount, 
                             BigDecimal withholdingTaxAmount, BigDecimal penaltyAmount,
                             BigDecimal interestAmount, BigDecimal totalTaxLiability, 
                             Boolean isSmallBusiness, LocalDate dueDate) {
        this.calculationId = calculationId;
        this.companyName = companyName;
        this.npwp = npwp;
        this.taxYear = taxYear;
        this.taxPeriod = taxPeriod;
        this.grossIncome = grossIncome;
        this.deductibleExpenses = deductibleExpenses;
        this.taxableIncome = taxableIncome;
        this.taxRate = taxRate;
        this.corporateTaxAmount = corporateTaxAmount;
        this.pphFinalAmount = pphFinalAmount;
        this.vatAmount = vatAmount;
        this.withholdingTaxAmount = withholdingTaxAmount;
        this.penaltyAmount = penaltyAmount;
        this.interestAmount = interestAmount;
        this.totalTaxLiability = totalTaxLiability;
        this.isSmallBusiness = isSmallBusiness;
        this.dueDate = dueDate;
        
        // Calculate effective tax rate
        if (grossIncome != null && grossIncome.compareTo(BigDecimal.ZERO) > 0) {
            this.effectiveRate = totalTaxLiability.divide(grossIncome, 4, RoundingMode.HALF_UP);
        } else {
            this.effectiveRate = BigDecimal.ZERO;
        }
    }
}
