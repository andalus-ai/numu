package tech.kayys.syirkah.commerce.subscription.domain;

import java.time.LocalDate;

/**
 * Billing cycle (blueprint §9 "Billing Cycle") — also defines how the
 * current entitlement period rolls on renewal.
 */
public enum BillingCycle {
    WEEKLY {
        @Override
        public LocalDate nextFrom(LocalDate periodStart) {
            return periodStart.plusWeeks(1);
        }
    },
    MONTHLY {
        @Override
        public LocalDate nextFrom(LocalDate periodStart) {
            return periodStart.plusMonths(1);
        }
    },
    QUARTERLY {
        @Override
        public LocalDate nextFrom(LocalDate periodStart) {
            return periodStart.plusMonths(3);
        }
    },
    ANNUAL {
        @Override
        public LocalDate nextFrom(LocalDate periodStart) {
            return periodStart.plusYears(1);
        }
    };

    /**
     * Exclusive end of the period starting on {@code periodStart}
     * (subtract one day for the inclusive last day).
     */
    public abstract LocalDate nextFrom(LocalDate periodStart);
}
