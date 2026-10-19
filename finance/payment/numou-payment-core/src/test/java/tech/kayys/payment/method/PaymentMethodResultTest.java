package tech.kayys.payment.method;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PaymentMethodResult
 */
class PaymentMethodResultTest {

    private PaymentMethodResult result;

    @BeforeEach
    void setUp() {
        result = new PaymentMethodResult();
    }

    @Test
    @DisplayName("Should create result with default values")
    void testDefaultValues() {
        assertNotNull(result);
        assertEquals("IDR", result.getCurrency());
        assertNotNull(result.getMetadata());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Should set success status")
    void testSuccess() {
        result.setSuccess(true);
        assertTrue(result.isSuccess());

        result.setSuccess(false);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("Should set transaction IDs")
    void testTransactionIds() {
        result.setTransactionId("TXN123");
        result.setExternalTransactionId("EXT456");

        assertEquals("TXN123", result.getTransactionId());
        assertEquals("EXT456", result.getExternalTransactionId());
    }

    @Test
    @DisplayName("Should set payment status")
    void testPaymentStatus() {
        result.setStatus(PaymentMethodResult.PaymentStatus.SUCCESS);
        assertEquals(PaymentMethodResult.PaymentStatus.SUCCESS, result.getStatus());

        result.setStatus(PaymentMethodResult.PaymentStatus.PENDING);
        assertEquals(PaymentMethodResult.PaymentStatus.PENDING, result.getStatus());
    }

    @Test
    @DisplayName("Should set QRIS information")
    void testQRISInfo() {
        result.setQrCodeUrl("https://example.com/qr.png");
        result.setQrCodeString("00020101021126640014ID.CO.QRIS.WWW");

        assertEquals("https://example.com/qr.png", result.getQrCodeUrl());
        assertEquals("00020101021126640014ID.CO.QRIS.WWW", result.getQrCodeString());
    }

    @Test
    @DisplayName("Should set virtual account information")
    void testVAInfo() {
        result.setVirtualAccountNumber("8888123456789");
        result.setBankCode("BCA");

        assertEquals("8888123456789", result.getVirtualAccountNumber());
        assertEquals("BCA", result.getBankCode());
    }

    @Test
    @DisplayName("Should set payment URL")
    void testPaymentUrl() {
        result.setPaymentUrl("https://payment.midtrans.com/redirect/123");
        assertEquals("https://payment.midtrans.com/redirect/123", result.getPaymentUrl());
    }

    @Test
    @DisplayName("Should set error information")
    void testErrorInfo() {
        result.setSuccess(false);
        result.setErrorMessage("Insufficient balance");
        result.setErrorCode("INSUFFICIENT_BALANCE");

        assertFalse(result.isSuccess());
        assertEquals("Insufficient balance", result.getErrorMessage());
        assertEquals("INSUFFICIENT_BALANCE", result.getErrorCode());
    }

    @Test
    @DisplayName("Should set expiry time")
    void testExpiryTime() {
        LocalDateTime expiry = LocalDateTime.now().plusHours(24);
        result.setExpiryTime(expiry);

        assertNotNull(result.getExpiryTime());
        assertTrue(result.getExpiryTime().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Should build result using builder pattern")
    void testBuilderPattern() {
        BigDecimal amount = new BigDecimal("100000");
        LocalDateTime expiry = LocalDateTime.now().plusHours(1);

        PaymentMethodResult builtResult = PaymentMethodResult.builder()
            .success(true)
            .transactionId("TXN789")
            .externalTransactionId("ORDER-999")
            .status(PaymentMethodResult.PaymentStatus.SUCCESS)
            .amount(amount)
            .qrCodeString("000201010211")
            .expiryTime(expiry)
            .build();

        assertTrue(builtResult.isSuccess());
        assertEquals("TXN789", builtResult.getTransactionId());
        assertEquals("ORDER-999", builtResult.getExternalTransactionId());
        assertEquals(PaymentMethodResult.PaymentStatus.SUCCESS, builtResult.getStatus());
        assertEquals(amount, builtResult.getAmount());
        assertEquals("000201010211", builtResult.getQrCodeString());
    }

    @Test
    @DisplayName("Should add metadata to result")
    void testMetadata() {
        result.addMetadata("bank", "BCA");
        result.addMetadata("va_type", "dynamic");

        assertEquals("BCA", result.getMetadata().get("bank"));
        assertEquals("dynamic", result.getMetadata().get("va_type"));
    }

    @Test
    @DisplayName("All payment statuses should be available")
    void testAllStatuses() {
        assertNotNull(PaymentMethodResult.PaymentStatus.SUCCESS);
        assertNotNull(PaymentMethodResult.PaymentStatus.PENDING);
        assertNotNull(PaymentMethodResult.PaymentStatus.FAILED);
        assertNotNull(PaymentMethodResult.PaymentStatus.CANCELLED);
        assertNotNull(PaymentMethodResult.PaymentStatus.EXPIRED);
        assertNotNull(PaymentMethodResult.PaymentStatus.REFUNDED);
        assertNotNull(PaymentMethodResult.PaymentStatus.PARTIALLY_REFUNDED);
    }
}
