package tech.kayys.payment.processor.bank.va;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VirtualAccount entity
 */
class VirtualAccountTest {

    @Test
    @DisplayName("Should create active dynamic VA by default")
    void testDefaultValues() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");

        assertEquals(VirtualAccount.VAType.DYNAMIC, va.getVaType());
        assertEquals(VirtualAccount.VAStatus.ACTIVE, va.getStatus());
        assertEquals(BigDecimal.ZERO, va.getTotalPaid());
        assertEquals(0, va.getPaymentCount().intValue());
        assertFalse(va.isExpired());
    }

    @Test
    @DisplayName("Should check if VA is active")
    void testIsActive() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setStatus(VirtualAccount.VAStatus.ACTIVE);

        assertTrue(va.isActive());

        va.setStatus(VirtualAccount.VAStatus.INACTIVE);
        assertFalse(va.isActive());
    }

    @Test
    @DisplayName("Should check if VA can accept payment")
    void testCanAcceptPayment() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setStatus(VirtualAccount.VAStatus.ACTIVE);
        va.setVaType(VirtualAccount.VAType.DYNAMIC);

        assertTrue(va.canAcceptPayment());

        // After payment, dynamic VA should not accept more payments
        va.recordPayment(new BigDecimal("100000"));
        assertFalse(va.canAcceptPayment());
    }

    @Test
    @DisplayName("Static VA should accept multiple payments")
    void testStaticVAAcceptMultiplePayments() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setStatus(VirtualAccount.VAStatus.ACTIVE);
        va.setVaType(VirtualAccount.VAType.STATIC);

        assertTrue(va.canAcceptPayment());

        va.recordPayment(new BigDecimal("100000"));
        assertTrue(va.canAcceptPayment()); // Static VA can accept more

        va.recordPayment(new BigDecimal("50000"));
        assertTrue(va.canAcceptPayment());
        assertEquals(2, va.getPaymentCount().intValue());
        assertEquals(new BigDecimal("150000"), va.getTotalPaid());
    }

    @Test
    @DisplayName("Should record payment correctly")
    void testRecordPayment() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");

        va.recordPayment(new BigDecimal("100000"));

        assertEquals(new BigDecimal("100000"), va.getTotalPaid());
        assertEquals(1, va.getPaymentCount().intValue());
    }

    @Test
    @DisplayName("Should validate fixed amount")
    void testValidAmountFixed() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setFixedAmount(new BigDecimal("100000"));

        assertTrue(va.isValidAmount(new BigDecimal("100000")));
        assertFalse(va.isValidAmount(new BigDecimal("50000")));
        assertFalse(va.isValidAmount(new BigDecimal("150000")));
    }

    @Test
    @DisplayName("Should validate amount within range")
    void testValidAmountRange() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setMinAmount(new BigDecimal("10000"));
        va.setMaxAmount(new BigDecimal("500000"));

        assertTrue(va.isValidAmount(new BigDecimal("100000")));
        assertTrue(va.isValidAmount(new BigDecimal("10000")));
        assertTrue(va.isValidAmount(new BigDecimal("500000")));
        assertFalse(va.isValidAmount(new BigDecimal("5000")));
        assertFalse(va.isValidAmount(new BigDecimal("1000000")));
    }

    @Test
    @DisplayName("Should mark VA as expired")
    void testExpired() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setStatus(VirtualAccount.VAStatus.ACTIVE);

        va.setExpired(true);

        assertFalse(va.isActive());
        assertFalse(va.canAcceptPayment());
    }

    @Test
    @DisplayName("Should handle multiple payments")
    void testMultiplePayments() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setVaType(VirtualAccount.VAType.STATIC);

        va.recordPayment(new BigDecimal("100000"));
        va.recordPayment(new BigDecimal("50000"));
        va.recordPayment(new BigDecimal("25000"));

        assertEquals(3, va.getPaymentCount().intValue());
        assertEquals(new BigDecimal("175000"), va.getTotalPaid());
    }

    @Test
    @DisplayName("Dynamic VA should deactivate after first payment")
    void testDynamicVADeactivates() {
        VirtualAccount va = new VirtualAccount();
        va.setVaNumber("700131234567890");
        va.setBankCode("BCA");
        va.setVaType(VirtualAccount.VAType.DYNAMIC);
        va.setStatus(VirtualAccount.VAStatus.ACTIVE);

        va.recordPayment(new BigDecimal("100000"));

        assertEquals(VirtualAccount.VAStatus.INACTIVE, va.getStatus());
        assertFalse(va.canAcceptPayment());
    }
}
