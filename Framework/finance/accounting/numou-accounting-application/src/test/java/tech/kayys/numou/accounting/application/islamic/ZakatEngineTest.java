package tech.kayys.syirkah.accounting.application.islamic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.islamic.IslamicContractType;
import tech.kayys.syirkah.accounting.domain.islamic.SukukCertificate;
import tech.kayys.syirkah.accounting.domain.islamic.ZakatCalculation;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ZakatEngineTest {

    private ZakatEngine zakatEngine;
    private IslamicFinanceService islamicService;

    @BeforeEach
    void setUp() {
        zakatEngine = new ZakatEngine();
        islamicService = new IslamicFinanceService(zakatEngine);
    }

    @Test
    void testZakatCalculationWhenNisabMet() {
        // Gold price = $80/g -> Nisab = 85g * 80 = $6,800
        BigDecimal goldPrice = new BigDecimal("80.00");
        BigDecimal currentAssets = new BigDecimal("200000.00");
        BigDecimal currentLiab = new BigDecimal("50000.00");
        // Base = 150,000 > 6,800
        // Lunar year rate = 2.5% -> 150,000 * 0.025 = 3,750.00
        ZakatCalculation calc = islamicService.assessCorporateZakat(currentAssets, currentLiab, goldPrice, false);

        assertTrue(calc.nisabMet());
        assertEquals(new BigDecimal("150000.00"), calc.zakatBase());
        assertEquals(new BigDecimal("6800.00"), calc.nisabThreshold());
        assertEquals(new BigDecimal("3750.00"), calc.zakatPayable());
    }

    @Test
    void testZakatZeroWhenBelowNisab() {
        BigDecimal goldPrice = new BigDecimal("100.00"); // Nisab = 8,500
        BigDecimal currentAssets = new BigDecimal("10000.00");
        BigDecimal currentLiab = new BigDecimal("5000.00"); // Base = 5,000 < 8,500

        ZakatCalculation calc = islamicService.assessCorporateZakat(currentAssets, currentLiab, goldPrice, false);
        assertFalse(calc.nisabMet());
        assertEquals(BigDecimal.ZERO, calc.zakatPayable());
    }

    @Test
    void testSukukRegistration() {
        SukukCertificate cert = islamicService.registerSukuk(
                "SK-01", "Green Solar Sukuk", IslamicContractType.IJARA,
                "SOLAR_PARK_01", new BigDecimal("5000000.00"), new BigDecimal("0.065"), LocalDate.of(2030, 1, 1)
        );

        assertEquals("SK-01", cert.certificateId());
        assertEquals(IslamicContractType.IJARA, cert.contractType());
        assertTrue(islamicService.findSukuk("SK-01").isPresent());
    }
}
