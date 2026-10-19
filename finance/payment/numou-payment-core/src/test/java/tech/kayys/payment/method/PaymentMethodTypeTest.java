package tech.kayys.payment.method;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PaymentMethodType enumeration
 */
class PaymentMethodTypeTest {

    @Test
    @DisplayName("Should create BANK_TRANSFER payment method")
    void testBankTransfer() {
        PaymentMethodType method = PaymentMethodType.BANK_TRANSFER;
        assertEquals("Bank Transfer", method.getDisplayName());
        assertEquals("transfer", method.getCode());
        assertTrue(method.isBankTransfer());
        assertFalse(method.isQRIS());
        assertFalse(method.isEWallet());
        assertFalse(method.isFintech());
        assertFalse(method.isCard());
        assertFalse(method.isCashPayment());
    }

    @Test
    @DisplayName("Should identify VA BCA as bank transfer")
    void testVABCA() {
        PaymentMethodType method = PaymentMethodType.VA_BCA;
        assertEquals("Virtual Account BCA", method.getDisplayName());
        assertEquals("va_bca", method.getCode());
        assertTrue(method.isBankTransfer());
    }

    @Test
    @DisplayName("Should identify VA Mandiri as bank transfer")
    void testVAMandiri() {
        PaymentMethodType method = PaymentMethodType.VA_MANDIRI;
        assertTrue(method.isBankTransfer());
        assertEquals("va_mandiri", method.getCode());
    }

    @Test
    @DisplayName("Should identify QRIS methods")
    void testQRISMethods() {
        assertTrue(PaymentMethodType.QRIS.isQRIS());
        assertTrue(PaymentMethodType.QRIS_DANA.isQRIS());
        assertTrue(PaymentMethodType.QRIS_OVO.isQRIS());
        assertTrue(PaymentMethodType.QRIS_GOPAY.isQRIS());
        assertTrue(PaymentMethodType.QRIS_LINKAJA.isQRIS());
    }

    @Test
    @DisplayName("Should identify e-wallet methods")
    void testEWalletMethods() {
        assertTrue(PaymentMethodType.OVO.isEWallet());
        assertTrue(PaymentMethodType.GOPAY.isEWallet());
        assertTrue(PaymentMethodType.DANA.isEWallet());
        assertTrue(PaymentMethodType.LINKAJA.isEWallet());
        assertTrue(PaymentMethodType.SHOPEEPAY.isEWallet());
        assertTrue(PaymentMethodType.E_WALLET.isEWallet());
    }

    @Test
    @DisplayName("Should identify fintech methods")
    void testFintechMethods() {
        assertTrue(PaymentMethodType.KREDIVO.isFintech());
        assertTrue(PaymentMethodType.AKULAKU.isFintech());
        assertTrue(PaymentMethodType.INDODANA.isFintech());
        assertTrue(PaymentMethodType.FINTECH.isFintech());
    }

    @Test
    @DisplayName("Should identify card methods")
    void testCardMethods() {
        assertTrue(PaymentMethodType.CREDIT_CARD.isCard());
        assertTrue(PaymentMethodType.DEBIT_CARD.isCard());
        assertFalse(PaymentMethodType.QRIS.isCard());
    }

    @Test
    @DisplayName("Should identify cash payment methods")
    void testCashPaymentMethods() {
        assertTrue(PaymentMethodType.ALFAMART.isCashPayment());
        assertTrue(PaymentMethodType.INDOMARET.isCashPayment());
        assertFalse(PaymentMethodType.QRIS.isCashPayment());
    }

    @Test
    @DisplayName("Should have correct code for all methods")
    void testAllMethodsHaveCodes() {
        for (PaymentMethodType method : PaymentMethodType.values()) {
            assertNotNull(method.getCode(), "Method " + method.name() + " should have a code");
            assertFalse(method.getCode().isEmpty(), "Method " + method.name() + " code should not be empty");
        }
    }

    @Test
    @DisplayName("Should have correct display name for all methods")
    void testAllMethodsHaveDisplayNames() {
        for (PaymentMethodType method : PaymentMethodType.values()) {
            assertNotNull(method.getDisplayName(), "Method " + method.name() + " should have a display name");
            assertFalse(method.getDisplayName().isEmpty(), 
                "Method " + method.name() + " display name should not be empty");
        }
    }
}
