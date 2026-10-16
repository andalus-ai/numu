package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.islamic.IslamicFinanceService;
import tech.kayys.syirkah.accounting.application.islamic.ZakatEngine;
import tech.kayys.syirkah.accounting.domain.islamic.IslamicContractType;
import tech.kayys.syirkah.accounting.domain.islamic.SukukCertificate;
import tech.kayys.syirkah.accounting.domain.islamic.ZakatCalculation;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class IslamicFinanceResourceTest {

    private IslamicFinanceResource resource;

    @BeforeEach
    void setUp() {
        resource = new IslamicFinanceResource();
        resource.islamicService = new IslamicFinanceService(new ZakatEngine());
    }

    @Test
    void testAssessZakatAndRegisterSukuk() {
        var zakatReq = new IslamicFinanceResource.AssessZakatRequest(
                new BigDecimal("500000.00"), new BigDecimal("100000.00"), new BigDecimal("85.00"), false
        );
        Response zakatResp = resource.assessZakat(zakatReq).await().indefinitely();
        assertEquals(200, zakatResp.getStatus());
        ZakatCalculation calc = (ZakatCalculation) zakatResp.getEntity();
        assertTrue(calc.nisabMet());
        assertEquals(new BigDecimal("400000.00"), calc.zakatBase());
        assertEquals(new BigDecimal("10000.00"), calc.zakatPayable());

        // Sukuk registration
        var sukukReq = new IslamicFinanceResource.RegisterSukukRequest(
                "SK-AIRPORT-01", "Airport Expansion Sukuk", IslamicContractType.IJARA,
                "AIRPORT_TERMINAL_2", new BigDecimal("10000000.00"), new BigDecimal("0.07"), LocalDate.of(2035, 12, 31)
        );
        Response sukukResp = resource.registerSukuk(sukukReq).await().indefinitely();
        assertEquals(201, sukukResp.getStatus());
        SukukCertificate cert = (SukukCertificate) sukukResp.getEntity();
        assertEquals("SK-AIRPORT-01", cert.certificateId());

        Response getSukukResp = resource.getSukuk("SK-AIRPORT-01").await().indefinitely();
        assertEquals(200, getSukukResp.getStatus());
    }
}
