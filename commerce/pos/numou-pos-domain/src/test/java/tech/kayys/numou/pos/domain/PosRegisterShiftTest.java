package tech.kayys.numou.pos.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PosRegisterShiftTest {

    @Test
    void moneyArithmeticRequiresMatchingCurrency() {
        Money idr = new Money(new BigDecimal("12500"), "IDR");
        assertEquals(new BigDecimal("15000"), idr.plus(new Money(new BigDecimal("2500"), "IDR")).amount());
        assertThrows(IllegalArgumentException.class,
                () -> idr.plus(new Money(new BigDecimal("1.00"), "USD")));
    }

    @Test
    void registerTrimsCodeAndPreservesTenantAndLocationIdentity() {
        UUID tenant = UUID.randomUUID();
        Register register = new Register(UUID.randomUUID(), tenant, UUID.randomUUID(),
                UUID.randomUUID(), "  FRONT-01  ", true);

        assertEquals("FRONT-01", register.code());
        assertEquals(tenant, register.tenantId());
        assertFalse(register.disable().enabled());
        assertTrue(register.enable().enabled());
    }

    @Test
    void shiftCanBeClosedOnceWithMatchingCurrencyAndValidTime() {
        Instant opened = Instant.parse("2026-10-29T08:00:00Z");
        RegisterShift shift = RegisterShift.open(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), opened, new Money(new BigDecimal("100000"), "IDR"));

        RegisterShift closed = shift.close(opened.plusSeconds(8 * 60 * 60),
                new Money(new BigDecimal("450000"), "IDR"));

        assertEquals(RegisterShift.Status.OPEN, shift.status(), "closing returns a new immutable snapshot");
        assertEquals(RegisterShift.Status.CLOSED, closed.status());
        assertEquals(new BigDecimal("450000"), closed.countedCash().amount());
        assertThrows(IllegalStateException.class,
                () -> closed.close(opened.plusSeconds(9 * 60 * 60), new Money(new BigDecimal("1"), "IDR")));
    }

    @Test
    void shiftRejectsNegativeFloatAndCurrencyMismatch() {
        assertThrows(IllegalArgumentException.class, () -> RegisterShift.open(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                new Money(new BigDecimal("-1"), "IDR")));

        RegisterShift shift = RegisterShift.open(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-29T08:00:00Z"),
                new Money(new BigDecimal("100"), "IDR"));
        assertThrows(IllegalArgumentException.class,
                () -> shift.close(Instant.parse("2026-10-29T09:00:00Z"), new Money(new BigDecimal("10"), "USD")));
    }

    @Test
    void shiftRejectsCloseBeforeOpen() {
        RegisterShift shift = RegisterShift.open(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-29T08:00:00Z"),
                Money.zero("IDR"));
        assertThrows(IllegalArgumentException.class,
                () -> shift.close(Instant.parse("2026-10-29T07:59:59Z"), Money.zero("IDR")));
    }
}
