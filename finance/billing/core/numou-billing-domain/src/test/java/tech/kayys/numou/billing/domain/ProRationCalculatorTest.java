package tech.kayys.numou.billing.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.billing.domain.valueobject.ProRationCalculator;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class ProRationCalculatorTest {

    static Instant at(String iso) {
        return Instant.parse(iso);
    }

    static void assertAmount(String expected, Money m) {
        assertEquals(0, new BigDecimal(expected).compareTo(m.getAmount()), "was " + m.getAmount());
    }

    @Test
    void aPartMonthPaysForTheDaysUsed() {
        // 10 January to the end of the month: 22 of 31 days
        assertAmount("70.967700", ProRationCalculator.calculateMonthlyProRation(Money.of(100L, "USD"),
                at("2026-01-10T00:00:00Z"), at("2026-01-31T00:00:00Z")));
    }

    @Test
    void anActivationBeforeTheMonthNeverPaysMoreThanTheMonth() {
        assertAmount("100.000000", ProRationCalculator.calculateMonthlyProRation(Money.of(100L, "USD"),
                at("2025-11-20T00:00:00Z"), at("2026-01-31T00:00:00Z")));
    }

    @Test
    void refundIsTheUnusedShareOfThePaidAmount() {
        assertAmount("50.000000", ProRationCalculator.calculateRefundProRation(Money.of(100L, "USD"),
                at("2026-01-01T00:00:00Z"), at("2026-01-31T00:00:00Z"), at("2026-01-16T00:00:00Z")));
        assertAmount("100", ProRationCalculator.calculateRefundProRation(Money.of(100L, "USD"),
                at("2026-01-01T00:00:00Z"), at("2026-01-31T00:00:00Z"), at("2025-12-31T00:00:00Z")));
        assertAmount("0", ProRationCalculator.calculateRefundProRation(Money.of(100L, "USD"),
                at("2026-01-01T00:00:00Z"), at("2026-01-31T00:00:00Z"), at("2026-02-05T00:00:00Z")));
    }

    @Test
    void theResultDoesNotDependOnTheServersTimeZone() {
        TimeZone before = TimeZone.getDefault();
        try {
            // 23:30 UTC on 31 January is already 1 February in Jakarta
            Money utc, jakarta;
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            utc = ProRationCalculator.calculateMonthlyProRation(Money.of(100L, "USD"),
                    at("2026-01-10T00:00:00Z"), at("2026-01-31T23:30:00Z"));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"));
            jakarta = ProRationCalculator.calculateMonthlyProRation(Money.of(100L, "USD"),
                    at("2026-01-10T00:00:00Z"), at("2026-01-31T23:30:00Z"));
            assertEquals(0, utc.getAmount().compareTo(jakarta.getAmount()));
        } finally {
            TimeZone.setDefault(before);
        }
    }
}
