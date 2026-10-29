package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.kayys.numou.numoudomain.Fixtures.*;

class StaffContractCheckerTest {

    private static final SyirkahContract CONTRACT = mudharabah(100_000_000, ratio(A, 40, B, 60));

    private static StaffContract.Wage fixed() {
        return new StaffContract.Wage(StaffContract.WageBasis.FIXED_AMOUNT, idr(3_000_000), "monthly");
    }

    private static StaffContract good() {
        return new StaffContract(StaffContract.Employer.SYIRKAH, null, "Accountant for the syirkah's books",
                "12 months", "7 hours a day", fixed(), List.of(), false);
    }

    private static List<String> codes(StaffContract s) {
        return StaffContractChecker.check(CONTRACT, s).stream().map(FormationFinding::code).toList();
    }

    @Test
    void aCompleteContractWithFixedWagePasses() {
        assertEquals(List.of(), StaffContractChecker.check(CONTRACT, good()));
    }

    @Test
    void hiringByOnePartnerIsBatil() {
        StaffContract s = new StaffContract(StaffContract.Employer.INDIVIDUAL_PARTNER, null, "Accountant", "12 months",
                "7 hours a day", fixed(), List.of(), false);
        var f = StaffContractChecker.check(CONTRACT, s);
        assertEquals("STAFF_HIRED_BY_PARTNER", f.get(0).code());
        assertEquals(AkadDefect.BATIL, f.get(0).defect());
    }

    @Test
    void aWageBasedOnSalesIsRejected() {
        StaffContract s = new StaffContract(StaffContract.Employer.SYIRKAH, null, "Salesperson", "12 months",
                "8 hours a day", new StaffContract.Wage(StaffContract.WageBasis.PERCENT_OF_SALES, null, null),
                List.of(), false);
        assertTrue(codes(s).contains("WAGE_NOT_FIXED"));
    }

    @Test
    void aWorkingPartnerCannotBeSalaried() {
        // B is the manager of the mudharabah
        StaffContract manager = new StaffContract(StaffContract.Employer.SYIRKAH, B, "Manager", "12 months",
                "full time", fixed(), List.of(), false);
        assertTrue(codes(manager).contains("MANAGER_SALARY"));
        // A is a capital provider: not barred by this rule
        StaffContract investor = new StaffContract(StaffContract.Employer.SYIRKAH, A, "Driver", "12 months",
                "4 hours a day", fixed(), List.of(), false);
        assertEquals(List.of(), codes(investor));
    }

    @Test
    void vagueTermsAreEachReported() {
        StaffContract s = new StaffContract(StaffContract.Employer.SYIRKAH, null, " ", null, "",
                new StaffContract.Wage(StaffContract.WageBasis.FIXED_AMOUNT, null, null), List.of("Uniform"), false);
        List<String> c = codes(s);
        assertTrue(c.containsAll(List.of("WORK_TYPE_MISSING", "WORK_PERIOD_MISSING", "EFFORT_MISSING",
                "WAGE_AMOUNT_MISSING", "PAY_PERIOD_MISSING", "JALI_REVIEW_MISSING")), c.toString());
    }

    @Test
    void requirePassingThrows() {
        StaffContract bad = new StaffContract(StaffContract.Employer.INDIVIDUAL_PARTNER, null, "x", "y", "z", fixed(),
                List.of(), false);
        assertThrows(BusinessRuleViolation.class, () -> StaffContractChecker.requirePassing(CONTRACT, bad));
    }
}
