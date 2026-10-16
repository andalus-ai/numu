package tech.kayys.syirkah.accounting.application.fund;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.fund.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FundAccountingServiceTest {

    private FundAccountingService fundService;

    @BeforeEach
    void setUp() {
        fundService = new FundAccountingService();
    }

    @Test
    void testFundAndGrantLifecycle() {
        FundId generalFundId = FundId.of("FUND-GEN");
        Fund generalFund = fundService.createFund(generalFundId, "100", "General Operating Fund", FundType.UNRESTRICTED, new BigDecimal("50000.00"));
        assertEquals(new BigDecimal("50000.00"), generalFund.balance());

        FundId researchFundId = FundId.of("FUND-RES");
        Fund researchFund = fundService.createFund(researchFundId, "200", "Research Endowment", FundType.TEMPORARILY_RESTRICTED, BigDecimal.ZERO);

        // Register Grant awarded to research fund
        GrantId grantId = GrantId.of("GRANT-UNICEF-01");
        Grant grant = fundService.registerGrant(grantId, "G-01", "UNICEF", researchFundId, new BigDecimal("100000.00"), LocalDate.of(2027, 12, 31));

        assertEquals(new BigDecimal("100000.00"), researchFund.balance());
        assertEquals(new BigDecimal("100000.00"), grant.remainingBalance());

        // Spend from grant
        fundService.recordGrantExpenditure(grantId, new BigDecimal("25000.00"));
        assertEquals(new BigDecimal("75000.00"), grant.remainingBalance());
        assertEquals(new BigDecimal("75000.00"), researchFund.balance());

        // Transfer between funds
        fundService.transfer(generalFundId, researchFundId, new BigDecimal("10000.00"));
        assertEquals(new BigDecimal("40000.00"), generalFund.balance());
        assertEquals(new BigDecimal("85000.00"), researchFund.balance());
    }

    @Test
    void testPermanentEndowmentCannotTransferOut() {
        FundId endowmentId = FundId.of("FUND-ENDOW");
        fundService.createFund(endowmentId, "300", "Permanent Endowment", FundType.PERMANENTLY_RESTRICTED, new BigDecimal("1000000.00"));

        FundId opId = FundId.of("FUND-OP");
        fundService.createFund(opId, "101", "Operating", FundType.UNRESTRICTED, BigDecimal.ZERO);

        assertThrows(IllegalStateException.class, () ->
                fundService.transfer(endowmentId, opId, new BigDecimal("50000.00")));
    }
}
