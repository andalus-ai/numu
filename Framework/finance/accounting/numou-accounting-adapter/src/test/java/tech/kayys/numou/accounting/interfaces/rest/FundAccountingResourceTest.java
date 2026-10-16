package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.fund.FundAccountingService;
import tech.kayys.syirkah.accounting.domain.fund.Fund;
import tech.kayys.syirkah.accounting.domain.fund.FundType;
import tech.kayys.syirkah.accounting.domain.fund.Grant;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FundAccountingResourceTest {

    private FundAccountingResource resource;

    @BeforeEach
    void setUp() {
        resource = new FundAccountingResource();
        resource.fundService = new FundAccountingService();
    }

    @Test
    void testFundAndGrantOperations() {
        var createFundReq = new FundAccountingResource.CreateFundRequest(
                "F-OPERATING", "101", "Operating Fund", FundType.UNRESTRICTED, new BigDecimal("10000.00")
        );
        Response fundResp = resource.createFund(createFundReq).await().indefinitely();
        assertEquals(201, fundResp.getStatus());
        Fund fund = (Fund) fundResp.getEntity();
        assertEquals("F-OPERATING", fund.id().value());

        // Register grant
        var grantReq = new FundAccountingResource.RegisterGrantRequest(
                "G-2026", "GR-1", "WHO", "F-OPERATING", new BigDecimal("50000.00"), LocalDate.of(2027, 6, 30)
        );
        Response grantResp = resource.registerGrant(grantReq).await().indefinitely();
        assertEquals(201, grantResp.getStatus());
        Grant grant = (Grant) grantResp.getEntity();
        assertEquals("G-2026", grant.id().value());

        // Spend from grant
        Response expResp = resource.recordExpenditure("G-2026", new FundAccountingResource.RecordExpenditureRequest(new BigDecimal("15000.00"))).await().indefinitely();
        assertEquals(200, expResp.getStatus());

        // Query fund
        Response getResp = resource.getFund("F-OPERATING").await().indefinitely();
        assertEquals(200, getResp.getStatus());
        Fund updated = (Fund) getResp.getEntity();
        // Initial 10k + Grant 50k - Spent 15k = 45k
        assertEquals(new BigDecimal("45000.00"), updated.balance());
    }
}
