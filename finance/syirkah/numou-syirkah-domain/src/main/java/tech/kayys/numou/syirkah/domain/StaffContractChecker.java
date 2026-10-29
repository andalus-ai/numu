package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks a staff (ijarah) contract against Hukum Muamalah Bab XIV (principle 10), XVI (section D) and XVII.
 *
 * <p>Software cannot judge whether a ja'li clause makes something unlawful lawful; it requires that someone
 * recorded that review when there are such clauses.
 */
public final class StaffContractChecker {

    private StaffContractChecker() {
    }

    public static List<FormationFinding> check(SyirkahContract contract, StaffContract staff) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(staff, "staff");
        List<FormationFinding> f = new ArrayList<>();

        if (staff.employer() == StaffContract.Employer.INDIVIDUAL_PARTNER) {
            f.add(new FormationFinding("STAFF_HIRED_BY_PARTNER", AkadDefect.BATIL,
                    "staff must be hired by the syirkah, not by one partner to represent himself; that partner's "
                            + "own akad would fall", "Bab XVI section D"));
        }
        if (staff.employeePartner() != null) {
            Partner p = contract.partners().stream().filter(x -> x.id().equals(staff.employeePartner()))
                    .findFirst().orElse(null);
            if (p != null && (p.role() == PartnerRole.MANAGER || p.contributesWork())) {
                f.add(new FormationFinding("MANAGER_SALARY", AkadDefect.UNCLASSIFIED,
                        "a partner who manages or works in the syirkah may not be paid a wage for it",
                        "Bab XIV principle 10"));
            }
        }
        if (blank(staff.workType())) {
            f.add(new FormationFinding("WORK_TYPE_MISSING", AkadDefect.FASAD,
                    "the kind of work must be stated specifically; a vague one makes the akad fasad",
                    "Bab XVII section C"));
        }
        if (blank(staff.workPeriod())) {
            f.add(new FormationFinding("WORK_PERIOD_MISSING", AkadDefect.UNCLASSIFIED,
                    "the length of the engagement must be stated", "Bab XVII section B"));
        }
        if (blank(staff.effort())) {
            f.add(new FormationFinding("EFFORT_MISSING", AkadDefect.UNCLASSIFIED,
                    "the effort expected (for example hours a day) must be stated", "Bab XVII section B"));
        }
        StaffContract.Wage w = staff.wage();
        if (w.basis() != StaffContract.WageBasis.FIXED_AMOUNT) {
            f.add(new FormationFinding("WAGE_NOT_FIXED", AkadDefect.UNCLASSIFIED,
                    "the wage must be a fixed nominal amount; a share of sales or profit is unclear (majhul) and "
                            + "can come to zero", "Bab XVII section F"));
        } else if (w.fixedAmount() == null || !w.fixedAmount().isPositive()) {
            f.add(new FormationFinding("WAGE_AMOUNT_MISSING", AkadDefect.UNCLASSIFIED,
                    "state the wage as a positive amount", "Bab XVII section F"));
        } else if (!w.fixedAmount().currency().equals(contract.currency())) {
            f.add(new FormationFinding("WAGE_CURRENCY", AkadDefect.UNCLASSIFIED,
                    "the wage must be in the syirkah's currency " + contract.currency().code(),
                    "Bab XVII section F"));
        }
        if (w.basis() == StaffContract.WageBasis.FIXED_AMOUNT && blank(w.payPeriod())) {
            f.add(new FormationFinding("PAY_PERIOD_MISSING", AkadDefect.UNCLASSIFIED,
                    "state how often the wage is paid", "Bab XVII section D"));
        }
        if (!staff.jaliClauses().isEmpty() && !staff.jaliReviewedLawful()) {
            f.add(new FormationFinding("JALI_REVIEW_MISSING", AkadDefect.INCOMPLETE,
                    "record the review that no extra clause makes lawful what is unlawful or the reverse",
                    "Bab XVII section G"));
        }
        return List.copyOf(f);
    }

    public static void requirePassing(SyirkahContract contract, StaffContract staff) {
        List<FormationFinding> findings = check(contract, staff);
        if (!findings.isEmpty()) {
            throw new BusinessRuleViolation("Staff contract is not valid: " + findings);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
