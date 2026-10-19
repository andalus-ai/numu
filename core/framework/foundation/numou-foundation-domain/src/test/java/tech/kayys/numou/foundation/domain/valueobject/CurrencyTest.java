package tech.kayys.numou.foundation.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyTest {

    @Test
    void normalizesCurrencyCode() {
        var currency = Currency.of(" idr ");

        assertEquals("IDR", currency.code());
    }

    @Test
    void rejectsInvalidCurrencyCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Currency.of("ID")
        );
    }

    @Test
    void supportsKnownCurrencies() {
        assertEquals("IDR", Currency.IDR().code());
        assertEquals("USD", Currency.USD().code());
    }

}
