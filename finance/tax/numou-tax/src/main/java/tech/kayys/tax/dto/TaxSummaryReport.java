package tech.kayys.tax.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TaxSummaryReport {
    
    public String companyName;
    public String npwp;
    public Integer taxYear;
    public BigDecimal totalGrossIncome;
    public BigDecimal totalTaxLiability;
    public BigDecimal totalPaid;
    public BigDecimal outstandingAmount;
    public Integer numberOfCalculations;
    public BigDecimal effectiveRate;
    
    public TaxSummaryReport() {}
    
    public TaxSummaryReport(String companyName, String npwp, Integer taxYear,
                          BigDecimal totalGrossIncome, BigDecimal totalTaxLiability,
                          BigDecimal totalPaid, Integer numberOfCalculations) {
        this.companyName = companyName;
        this.npwp = npwp;
        this.taxYear = taxYear;
        this.totalGrossIncome = totalGrossIncome;
        this.totalTaxLiability = totalTaxLiability;
        this.totalPaid = totalPaid;
        this.numberOfCalculations = numberOfCalculations;
        this.outstandingAmount = totalTaxLiability.subtract(totalPaid);
        
        if (totalGrossIncome != null && totalGrossIncome.compareTo(BigDecimal.ZERO) > 0) {
            this.effectiveRate = totalTaxLiability.divide(totalGrossIncome, 4, RoundingMode.HALF_UP);
        } else {
            this.effectiveRate = BigDecimal.ZERO;
        }
    }
}
