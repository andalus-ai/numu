package tech.kayys.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TaxCalculationService {
    // Standard tax rates by state or jurisdiction
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.10"); // 10%
    
    private Map<String, BigDecimal> stateTaxRates = new HashMap<>();
    
    @PostConstruct
    void init() {
        stateTaxRates.put("CA", new BigDecimal("0.0725"));
        stateTaxRates.put("NY", new BigDecimal("0.045"));
        stateTaxRates.put("TX", new BigDecimal("0.0625"));
        stateTaxRates.put("ID_PPN", new BigDecimal("0.11")); // Indonesian PPN 11%
        stateTaxRates.put("ID_PB1", new BigDecimal("0.10")); // Indonesian Restaurant Tax PB1 10%
    }
    
    public BigDecimal calculateTaxRate(String codeOrAddress) {
        if (codeOrAddress == null) {
            return DEFAULT_TAX_RATE;
        }
        String upper = codeOrAddress.toUpperCase();
        for (Map.Entry<String, BigDecimal> entry : stateTaxRates.entrySet()) {
            if (upper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_TAX_RATE;
    }
    
    public BigDecimal calculateTax(BigDecimal amount, String codeOrAddress) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxRate = calculateTaxRate(codeOrAddress);
        return amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }
}
