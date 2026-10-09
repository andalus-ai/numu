package tech.kayys.syirkah.accounting.domain.currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.MonetaryRoundingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFxConversionServiceTest {

    private DefaultFxConversionService service;
    private Currency usd;
    private Currency idr;
    private Currency eur;

    @BeforeEach
    void setUp() {
        service = new DefaultFxConversionService();
        usd = Currency.USD();
        idr = Currency.IDR();
        eur = Currency.EUR();

        // 1 USD = 15,500 IDR
        service.registerRate(new ExchangeRate(
                "RATE-USD-IDR",
                usd,
                idr,
                new BigDecimal("15500.00"),
                ExchangeRateType.SPOT,
                ExchangeRateStatus.ACTIVE,
                Instant.now(),
                "BANK_INDONESIA"
        ));
    }

    @Test
    void testDirectConversion() {
        FxConversionRequest request = new FxConversionRequest(
                Money.of(new BigDecimal("100.00"), usd),
                idr,
                ExchangeRateType.SPOT,
                Instant.now(),
                MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS
        );

        FxConversionResult result = service.convert(request);

        assertEquals(new BigDecimal("1550000.00"), result.targetAmount().amount());
        assertEquals(idr, result.targetAmount().currency());
        assertEquals("RATE-USD-IDR", result.rateReference());
    }

    @Test
    void testReciprocalConversion() {
        FxConversionRequest request = new FxConversionRequest(
                Money.of(new BigDecimal("1550000.00"), idr),
                usd,
                ExchangeRateType.SPOT,
                Instant.now(),
                MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS
        );

        FxConversionResult result = service.convert(request);

        assertEquals(new BigDecimal("100.00"), result.targetAmount().amount());
        assertEquals(usd, result.targetAmount().currency());
        assertTrue(result.rateReference().startsWith("RECIPROCAL:"));
    }

    @Test
    void testIdenticalCurrencyThrows() {
        FxConversionRequest request = new FxConversionRequest(
                Money.of(new BigDecimal("100.00"), usd),
                usd,
                ExchangeRateType.SPOT,
                Instant.now()
        );

        assertThrows(IllegalArgumentException.class, () -> service.convert(request));
    }

    @Test
    void testMissingRateThrows() {
        FxConversionRequest request = new FxConversionRequest(
                Money.of(new BigDecimal("100.00"), usd),
                eur,
                ExchangeRateType.SPOT,
                Instant.now()
        );

        assertThrows(IllegalArgumentException.class, () -> service.convert(request));
    }
}
