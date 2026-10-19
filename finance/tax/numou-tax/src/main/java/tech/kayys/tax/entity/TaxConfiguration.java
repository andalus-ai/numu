package tech.kayys.tax.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tax_configurations")
public class TaxConfiguration extends PanacheEntity {
    
    @NotNull
    @Column(name = "tax_year")
    public Integer taxYear;
    
    @NotNull
    @Column(name = "corporate_tax_rate", precision = 5, scale = 4)
    public BigDecimal corporateTaxRate;
    
    @NotNull
    @Column(name = "small_business_rate", precision = 5, scale = 4)
    public BigDecimal smallBusinessRate;
    
    @NotNull
    @Column(name = "small_business_threshold", precision = 19, scale = 2)
    public BigDecimal smallBusinessThreshold;
    
    @NotNull
    @Column(name = "pph_final_rate", precision = 5, scale = 4)
    public BigDecimal pphFinalRate;
    
    @Column(name = "vat_rate", precision = 5, scale = 4)
    public BigDecimal vatRate = new BigDecimal("0.11"); // 11% PPN
    
    @Column(name = "luxury_tax_rate", precision = 5, scale = 4)
    public BigDecimal luxuryTaxRate = new BigDecimal("0.125"); // 12.5% PPnBM
    
    @Column(name = "withholding_tax_rate", precision = 5, scale = 4)
    public BigDecimal withholdingTaxRate = new BigDecimal("0.02"); // 2% PPh 23
    
    @Column(name = "effective_date")
    public LocalDate effectiveDate;
    
    @Column(name = "is_active")
    public Boolean isActive = true;
    
    // Static methods for queries
    public static TaxConfiguration findByYear(Integer year) {
        return find("taxYear = ?1 and isActive = true", year).firstResult();
    }
    
    public static TaxConfiguration findCurrent() {
        return find("isActive = true order by taxYear desc").firstResult();
    }
}
