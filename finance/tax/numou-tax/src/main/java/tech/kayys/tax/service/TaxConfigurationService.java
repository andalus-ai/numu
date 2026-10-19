package tech.kayys.tax.service;

import tech.kayys.tax.entity.TaxConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;

@ApplicationScoped
public class TaxConfigurationService {
    
    @Transactional
    public void initializeDefaultConfiguration() {
        // Check if configuration exists
        if (TaxConfiguration.count() == 0) {
            // Create default configuration for 2024
            TaxConfiguration config = new TaxConfiguration();
            config.taxYear = 2024;
            config.corporateTaxRate = new BigDecimal("0.22"); // 22%
            config.smallBusinessRate = new BigDecimal("0.125"); // 12.5%
            config.smallBusinessThreshold = new BigDecimal("50000000000"); // 50 billion IDR
            config.pphFinalRate = new BigDecimal("0.005"); // 0.5%
            config.effectiveDate = LocalDate.of(2024, 1, 1);
            config.isActive = true;
            config.persist();
            
            // Create configuration for 2025
            TaxConfiguration config2025 = new TaxConfiguration();
            config2025.taxYear = 2025;
            config2025.corporateTaxRate = new BigDecimal("0.22"); // 22%
            config2025.smallBusinessRate = new BigDecimal("0.125"); // 12.5%
            config2025.smallBusinessThreshold = new BigDecimal("50000000000"); // 50 billion IDR
            config2025.pphFinalRate = new BigDecimal("0.005"); // 0.5%
            config2025.effectiveDate = LocalDate.of(2025, 1, 1);
            config2025.isActive = true;
            config2025.persist();
        }
    }
    
    @Transactional
    public TaxConfiguration updateConfiguration(Integer year, BigDecimal corporateRate, 
                                              BigDecimal smallBusinessRate, BigDecimal threshold) {
        TaxConfiguration config = TaxConfiguration.findByYear(year);
        if (config == null) {
            config = new TaxConfiguration();
            config.taxYear = year;
            config.effectiveDate = LocalDate.of(year, 1, 1);
            config.isActive = true;
        }
        
        config.corporateTaxRate = corporateRate;
        config.smallBusinessRate = smallBusinessRate;
        config.smallBusinessThreshold = threshold;
        config.persist();
        
        return config;
    }
}
