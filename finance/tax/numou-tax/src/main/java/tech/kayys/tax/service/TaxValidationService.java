package tech.kayys.tax.service;

import tech.kayys.tax.dto.TaxCalculationRequest;
import tech.kayys.tax.entity.Company;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TaxValidationService {
    
    public void validateTaxCalculationRequest(TaxCalculationRequest request) {
        List<String> errors = new ArrayList<>();
        
        // Validate NPWP format
        if (!isValidNpwp(request.npwp)) {
            errors.add("Invalid NPWP format");
        }
        
        // Validate tax year
        if (request.taxYear < 2000 || request.taxYear > LocalDate.now().getYear()) {
            errors.add("Invalid tax year");
        }
        
        // Validate amounts
        if (request.grossIncome.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Gross income cannot be negative");
        }
        
        if (request.deductibleExpenses.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Deductible expenses cannot be negative");
        }
        
        // Business logic validation
        if (request.deductibleExpenses.compareTo(request.grossIncome) > 0) {
            errors.add("Deductible expenses cannot exceed gross income");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation errors: " + String.join(", ", errors));
        }
    }
    
    private boolean isValidNpwp(String npwp) {
        // Indonesian NPWP format: XX.XXX.XXX.X-XXX.XXX
        return npwp != null && npwp.matches("\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d{1}-\\d{3}\\.\\d{3}");
    }
    
    public void validateCompanyEligibility(Company company) {
        if (!Boolean.TRUE.equals(company.isActive)) {
            throw new IllegalArgumentException("Company is not active");
        }
        
        // Check if company is in good standing
        if (company.riskLevel == Company.RiskLevel.HIGH) {
            throw new IllegalArgumentException("Company has high risk level - manual review required");
        }
    }
}
