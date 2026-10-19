package tech.kayys.tax.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "companies")
public class Company extends PanacheEntity {
    
    @NotBlank
    @Column(name = "company_name")
    public String companyName;
    
    @NotBlank
    @Column(name = "npwp", unique = true)
    public String npwp;
    
    @NotBlank
    @Column(name = "company_type")
    public String companyType;
    
    @NotNull
    @Column(name = "establishment_date")
    public LocalDate establishmentDate;
    
    @Column(name = "business_field")
    public String businessField;
    
    @Column(name = "address")
    public String address;
    
    @Column(name = "annual_revenue", precision = 19, scale = 2)
    public BigDecimal annualRevenue;
    
    @Column(name = "is_small_business")
    public Boolean isSmallBusiness = false;
    
    @Column(name = "is_pkp") // Pengusaha Kena Pajak (VAT registered)
    public Boolean isPkp = false;
    
    @Column(name = "is_active")
    public Boolean isActive = true;
    
    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    public RiskLevel riskLevel = RiskLevel.LOW;
    
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<TaxCalculation> taxCalculations;
    
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<TaxPayment> taxPayments;
    
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<TaxReport> taxReports;
    
    public static Company findByNpwp(String npwp) {
        return find("npwp = ?1 and isActive = true", npwp).firstResult();
    }
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }
}
