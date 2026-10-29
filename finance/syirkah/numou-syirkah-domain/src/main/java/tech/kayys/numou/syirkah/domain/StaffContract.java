package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;

/**
 * The terms of an employment akad (ijarah of labour) offered by a syirkah, as Hukum Muamalah Bab XVI (section D)
 * and Bab XVII state them: the employer is the syirkah, and the benefit (kind of work, period, effort) and the wage
 * must all be clear (ma'lum).
 *
 * @param employer           who hires: the syirkah, or one partner acting for himself
 * @param employeePartner    set when the employee is also a partner of the syirkah
 * @param workType           kind of work (nau'ul-'amal); must be specific
 * @param workPeriod         length of the engagement (muddatul-'amal)
 * @param effort             effort expected (juhdul-'amal), for example hours per day
 * @param wage               the wage terms
 * @param jaliClauses        extra technical conditions agreed by the parties (uniform, overtime, leave...)
 * @param jaliReviewedLawful whether someone recorded that no ja'li clause makes lawful what is unlawful or the reverse
 */
public record StaffContract(Employer employer, PartnerId employeePartner, String workType, String workPeriod,
                            String effort, Wage wage, List<String> jaliClauses, boolean jaliReviewedLawful) {

    public enum Employer {
        /** The syirkah as a whole: every partner's employee. */
        SYIRKAH,
        /** One partner hiring in his own name, for example to do his own task for him. */
        INDIVIDUAL_PARTNER
    }

    public enum WageBasis {
        /** A fixed nominal amount for a pay period, for example Rp 3,000,000 a month. */
        FIXED_AMOUNT,
        /** A percentage of sales; unclear (majhul) under the book. */
        PERCENT_OF_SALES,
        /** A percentage of profit; unclear (majhul) for the same reason. */
        PERCENT_OF_PROFIT,
        OTHER
    }

    public record Wage(WageBasis basis, Money fixedAmount, String payPeriod) {
        public Wage {
            Objects.requireNonNull(basis, "basis");
        }
    }

    public StaffContract {
        Objects.requireNonNull(employer, "employer");
        Objects.requireNonNull(wage, "wage");
        jaliClauses = jaliClauses == null ? List.of() : List.copyOf(jaliClauses);
    }
}
