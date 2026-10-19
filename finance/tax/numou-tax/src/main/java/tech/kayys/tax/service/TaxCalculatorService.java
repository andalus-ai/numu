package tech.kayys.tax.service;

import tech.kayys.tax.entity.Company;
import tech.kayys.tax.entity.TaxCalculation;
import tech.kayys.tax.entity.TaxConfiguration;
import tech.kayys.tax.dto.TaxCalculationRequest;
import tech.kayys.tax.dto.TaxCalculationResult;
import tech.kayys.tax.dto.TaxSummaryReport;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class TaxCalculatorService {
    
    private static final Logger LOG = Logger.getLogger(TaxCalculatorService.class.getName());
    
    @Inject
    TaxValidationService taxValidationService;
    
    @Inject
    TaxNotificationService taxNotificationService;
    
    @Transactional
    public TaxCalculationResult calculateCorporateTax(TaxCalculationRequest request) {
        LOG.info("Calculating corporate tax for NPWP: " + request.npwp);
        
        // Validate company
        Company company = Company.findByNpwp(request.npwp);
        if (company == null) {
            throw new IllegalArgumentException("Company not found with NPWP: " + request.npwp);
        }
        
        // Validate business rules
        taxValidationService.validateTaxCalculationRequest(request);
        
        // Get tax configuration
        TaxConfiguration config = getTaxConfiguration(request.taxYear);
        
        // Calculate taxable income
        BigDecimal taxableIncome = calculateTaxableIncome(request.grossIncome, request.deductibleExpenses);
        
        // Determine tax rate
        BigDecimal taxRate = determineTaxRate(company, taxableIncome, config);
        
        // Calculate various taxes
        BigDecimal corporateTax = calculateCorporateTax(taxableIncome, taxRate);
        BigDecimal pphFinal = calculatePphFinal(request.grossIncome, config, company.businessField);
        BigDecimal vatAmount = calculateVAT(request.grossIncome, config, company.isPkp);
        BigDecimal withholdingTax = calculateWithholdingTax(request.grossIncome, config);
        
        // Calculate penalties and interest for late payments
        BigDecimal penalty = calculatePenalty(corporateTax, request.taxYear);
        BigDecimal interest = calculateInterest(corporateTax, request.taxYear);
        
        // Total tax liability
        BigDecimal totalTax = corporateTax.add(pphFinal).add(vatAmount).add(withholdingTax).add(penalty).add(interest);
        
        // Save calculation
        TaxCalculation calculation = createTaxCalculation(request, company, taxableIncome, taxRate, 
            corporateTax, pphFinal, vatAmount, withholdingTax, totalTax);
        
        // Send notification if amount exceeds threshold
        if (totalTax.compareTo(new BigDecimal("100000000")) > 0) { // 100 million IDR
            taxNotificationService.sendHighTaxLiabilityNotification(company, totalTax);
        }
        
        return new TaxCalculationResult(
            calculation.id,
            company.companyName,
            company.npwp,
            request.taxYear,
            request.taxPeriod,
            request.grossIncome,
            request.deductibleExpenses,
            taxableIncome,
            taxRate,
            corporateTax,
            pphFinal,
            vatAmount,
            withholdingTax,
            penalty,
            interest,
            totalTax,
            company.isSmallBusiness,
            calculation.dueDate
        );
    }
    
    @CacheResult(cacheName = "tax-config")
    public TaxConfiguration getTaxConfiguration(Integer taxYear) {
        TaxConfiguration config = TaxConfiguration.findByYear(taxYear);
        if (config == null) {
            config = TaxConfiguration.findCurrent();
            if (config == null) {
                throw new IllegalStateException("No tax configuration found");
            }
        }
        return config;
    }
    
    private BigDecimal calculateTaxableIncome(BigDecimal grossIncome, BigDecimal deductibleExpenses) {
        BigDecimal taxableIncome = grossIncome.subtract(deductibleExpenses);
        return taxableIncome.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : taxableIncome;
    }
    
    private BigDecimal calculateCorporateTax(BigDecimal taxableIncome, BigDecimal taxRate) {
        return taxableIncome.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateVAT(BigDecimal grossIncome, TaxConfiguration config, Boolean isPkp) {
        if (!Boolean.TRUE.equals(isPkp)) return BigDecimal.ZERO;
        return grossIncome.multiply(config.vatRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateWithholdingTax(BigDecimal grossIncome, TaxConfiguration config) {
        return grossIncome.multiply(config.withholdingTaxRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculatePenalty(BigDecimal taxAmount, Integer taxYear) {
        LocalDate dueDate = LocalDate.of(taxYear + 1, 3, 31);
        if (LocalDate.now().isAfter(dueDate)) {
            // 2% per month penalty
            long monthsLate = java.time.temporal.ChronoUnit.MONTHS.between(dueDate, LocalDate.now());
            BigDecimal penaltyRate = new BigDecimal("0.02").multiply(BigDecimal.valueOf(monthsLate));
            return taxAmount.multiply(penaltyRate).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
    
    private BigDecimal calculateInterest(BigDecimal taxAmount, Integer taxYear) {
        LocalDate dueDate = LocalDate.of(taxYear + 1, 3, 31);
        if (LocalDate.now().isAfter(dueDate)) {
            // 1.5% per month interest
            long monthsLate = java.time.temporal.ChronoUnit.MONTHS.between(dueDate, LocalDate.now());
            BigDecimal interestRate = new BigDecimal("0.015").multiply(BigDecimal.valueOf(monthsLate));
            return taxAmount.multiply(interestRate).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
    
    private TaxCalculation createTaxCalculation(TaxCalculationRequest request, Company company, 
            BigDecimal taxableIncome, BigDecimal taxRate, BigDecimal corporateTax, 
            BigDecimal pphFinal, BigDecimal vatAmount, BigDecimal withholdingTax, BigDecimal totalTax) {
        
        TaxCalculation calculation = new TaxCalculation();
        calculation.company = company;
        calculation.taxYear = request.taxYear;
        calculation.taxPeriod = request.taxPeriod;
        calculation.grossIncome = request.grossIncome;
        calculation.deductibleExpenses = request.deductibleExpenses;
        calculation.taxableIncome = taxableIncome;
        calculation.taxRate = taxRate;
        calculation.taxAmount = corporateTax;
        calculation.pphFinalAmount = pphFinal;
        calculation.vatAmount = vatAmount;
        calculation.withholdingTaxAmount = withholdingTax;
        calculation.totalTaxLiability = totalTax;
        calculation.notes = request.notes;
        calculation.calculatedBy = request.calculatedBy;
        calculation.persist();
        
        return calculation;
    }
    
    private BigDecimal determineTaxRate(Company company, BigDecimal taxableIncome, TaxConfiguration config) {
        // Check if company qualifies for small business rate
        if (Boolean.TRUE.equals(company.isSmallBusiness) && taxableIncome.compareTo(config.smallBusinessThreshold) <= 0) {
            return config.smallBusinessRate;
        }
        
        // Check annual revenue for small business qualification
        if (company.annualRevenue != null && 
            company.annualRevenue.compareTo(new BigDecimal("50000000000")) <= 0) { // 50 billion IDR
            return config.smallBusinessRate;
        }
        
        return config.corporateTaxRate;
    }
    
    private BigDecimal calculatePphFinal(BigDecimal grossIncome, TaxConfiguration config, String businessField) {
        // Different rates for different business fields
        if ("CONSTRUCTION".equals(businessField)) {
            return grossIncome.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
        } else if ("RENTAL".equals(businessField)) {
            return grossIncome.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        }
        return grossIncome.multiply(config.pphFinalRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    public List<TaxCalculation> getCompanyTaxHistory(String npwp) {
        Company company = Company.findByNpwp(npwp);
        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }
        
        return TaxCalculation.list("company = ?1 order by taxYear desc, taxPeriod desc", company);
    }
    
    public TaxSummaryReport generateTaxSummaryReport(String npwp, Integer taxYear) {
        Company company = Company.findByNpwp(npwp);
        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }
        
        List<TaxCalculation> calculations = TaxCalculation.list("company = ?1 and taxYear = ?2", company, taxYear);
        
        BigDecimal totalGrossIncome = calculations.stream()
            .map(calc -> calc.grossIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalTaxLiability = calculations.stream()
            .map(calc -> calc.totalTaxLiability)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalPaid = (company.taxPayments != null) ? company.taxPayments.stream()
            .filter(payment -> payment.paymentDate != null && payment.paymentDate.getYear() == taxYear)
            .map(payment -> payment.paymentAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;
            
        return new TaxSummaryReport(company.companyName, npwp, taxYear, 
            totalGrossIncome, totalTaxLiability, totalPaid, calculations.size());
    }
    
    public TaxCalculationResult recalculateTax(Long calculationId, TaxCalculationRequest request) {
        TaxCalculation existing = TaxCalculation.findById(calculationId);
        if (existing == null) {
            throw new IllegalArgumentException("Tax calculation not found");
        }
        
        // Create new calculation with updated values
        request.npwp = existing.company.npwp;
        return calculateCorporateTax(request);
    }
    
    @Transactional
    public void approveTaxCalculation(Long calculationId, String reviewedBy) {
        TaxCalculation calculation = TaxCalculation.findById(calculationId);
        if (calculation == null) {
            throw new IllegalArgumentException("Tax calculation not found");
        }
        
        calculation.status = TaxCalculation.CalculationStatus.APPROVED;
        calculation.reviewedBy = reviewedBy;
        calculation.reviewedDate = java.time.LocalDateTime.now();
        calculation.persist();
        
        // Send notification
        taxNotificationService.sendTaxApprovalNotification(calculation.company, calculation);
    }
}
