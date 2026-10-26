package tech.kayys.numou.hospitality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StayQuoteTest {
    private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-01-01T10:15:00Z");

    @Test
    void freezesNightsAndCalculatesConsistentTotal() {
        StayQuote quote = new StayQuote(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-04"), 2, "IDR",
                List.of(new BigDecimal("500000"), new BigDecimal("550000"), new BigDecimal("600000")),
                new BigDecimal("25000"), new BigDecimal("50000"), new BigDecimal("1725000"),
                "cancellation-v1", CREATED, EXPIRES);
        assertThat(quote.nights()).isEqualTo(3);
        assertThat(quote.total()).isEqualByComparingTo("1725000");
        assertThat(quote.isExpiredAt(CREATED.plusSeconds(899))).isFalse();
        assertThat(quote.isExpiredAt(EXPIRES)).isTrue();
        assertThatThrownBy(() -> quote.nightlyRates().add(BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidStayRateCountAndMismatchedTotal() {
        assertThatThrownBy(() -> new StayQuote(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-03"), 1, "IDR",
                List.of(new BigDecimal("100")), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                "policy-v1", CREATED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one amount per night");

        assertThatThrownBy(() -> new StayQuote(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-02"), 1, "IDR",
                List.of(new BigDecimal("100")), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("101"),
                "policy-v1", CREATED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total must equal");
    }

    @Test
    void rejectsNegativeMoneyAndNonExclusiveCheckout() {
        assertThatThrownBy(() -> new StayQuote(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-02-02"), LocalDate.parse("2026-02-01"), 1, "IDR", List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "policy-v1", CREATED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkOut must be after checkIn");

        assertThatThrownBy(() -> new StayQuote(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-02"), 1, "IDR",
                List.of(new BigDecimal("-1")), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"),
                "policy-v1", CREATED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nightly rate");
    }
}
