package tech.kayys.payment.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GatewayResponse
 */
class GatewayResponseTest {

    private GatewayResponse response;

    @BeforeEach
    void setUp() {
        response = new GatewayResponse();
    }

    @Test
    @DisplayName("Should create response with default values")
    void testDefaultValues() {
        assertNotNull(response);
        assertEquals("IDR", response.getCurrency());
        assertNotNull(response.getMetadata());
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("Should set success status")
    void testSuccess() {
        response.setSuccess(true);
        assertTrue(response.isSuccess());
    }

    @Test
    @DisplayName("Should set transaction IDs")
    void testTransactionIds() {
        response.setTransactionId("TXN123");
        response.setExternalTransactionId("EXT456");

        assertEquals("TXN123", response.getTransactionId());
        assertEquals("EXT456", response.getExternalTransactionId());
    }

    @Test
    @DisplayName("Should set status code and message")
    void testStatus() {
        response.setStatusCode("200");
        response.setStatusMessage("Success");

        assertEquals("200", response.getStatusCode());
        assertEquals("Success", response.getStatusMessage());
    }

    @Test
    @DisplayName("Should set payment status")
    void testPaymentStatus() {
        response.setPaymentStatus(GatewayResponse.PaymentStatus.SUCCESS);
        assertEquals(GatewayResponse.PaymentStatus.SUCCESS, response.getPaymentStatus());

        response.setPaymentStatus(GatewayResponse.PaymentStatus.PENDING);
        assertEquals(GatewayResponse.PaymentStatus.PENDING, response.getPaymentStatus());
    }

    @Test
    @DisplayName("Should set QRIS information")
    void testQRISInfo() {
        response.setQrCodeUrl("https://example.com/qr.png");
        response.setQrCodeString("00020101021126640014ID.CO.QRIS.WWW");

        assertEquals("https://example.com/qr.png", response.getQrCodeUrl());
        assertEquals("00020101021126640014ID.CO.QRIS.WWW", response.getQrCodeString());
    }

    @Test
    @DisplayName("Should set virtual account information")
    void testVAInfo() {
        response.setVirtualAccountNumber("8888123456789");
        response.setBankCode("BCA");

        assertEquals("8888123456789", response.getVirtualAccountNumber());
        assertEquals("BCA", response.getBankCode());
    }

    @Test
    @DisplayName("Should set payment URL")
    void testPaymentUrl() {
        response.setPaymentUrl("https://payment.midtrans.com/redirect/123");
        assertEquals("https://payment.midtrans.com/redirect/123", response.getPaymentUrl());
    }

    @Test
    @DisplayName("Should set error information")
    void testErrorInfo() {
        response.setSuccess(false);
        response.setErrorMessage("Transaction failed");
        response.setErrorCode("FAILED_TRANSACTION");

        assertFalse(response.isSuccess());
        assertEquals("Transaction failed", response.getErrorMessage());
        assertEquals("FAILED_TRANSACTION", response.getErrorCode());
    }

    @Test
    @DisplayName("Should set transaction and expiry time")
    void testTime() {
        LocalDateTime transactionTime = LocalDateTime.now();
        LocalDateTime expiryTime = LocalDateTime.now().plusHours(24);

        response.setTransactionTime(transactionTime);
        response.setExpiryTime(expiryTime);

        assertNotNull(response.getTransactionTime());
        assertNotNull(response.getExpiryTime());
        assertTrue(response.getExpiryTime().isAfter(response.getTransactionTime()));
    }

    @Test
    @DisplayName("Should build response using builder pattern")
    void testBuilderPattern() {
        BigDecimal amount = new BigDecimal("100000");
        LocalDateTime expiry = LocalDateTime.now().plusHours(1);

        GatewayResponse builtResponse = GatewayResponse.builder()
            .success(true)
            .transactionId("TXN789")
            .externalTransactionId("ORDER-999")
            .statusCode("200")
            .statusMessage("Success")
            .paymentStatus(GatewayResponse.PaymentStatus.SUCCESS)
            .amount(amount)
            .virtualAccountNumber("8888123456789")
            .bankCode("BCA")
            .expiryTime(expiry)
            .build();

        assertTrue(builtResponse.isSuccess());
        assertEquals("TXN789", builtResponse.getTransactionId());
        assertEquals(GatewayResponse.PaymentStatus.SUCCESS, builtResponse.getPaymentStatus());
        assertEquals(amount, builtResponse.getAmount());
        assertEquals("8888123456789", builtResponse.getVirtualAccountNumber());
        assertEquals("BCA", builtResponse.getBankCode());
    }

    @Test
    @DisplayName("Should add metadata to response")
    void testMetadata() {
        response.addMetadata("bank", "BCA");
        response.addMetadata("payment_type", "va");

        assertEquals("BCA", response.getMetadata().get("bank"));
        assertEquals("va", response.getMetadata().get("payment_type"));
    }

    @Test
    @DisplayName("All payment statuses should be available")
    void testAllStatuses() {
        assertNotNull(GatewayResponse.PaymentStatus.SUCCESS);
        assertNotNull(GatewayResponse.PaymentStatus.PENDING);
        assertNotNull(GatewayResponse.PaymentStatus.FAILED);
        assertNotNull(GatewayResponse.PaymentStatus.CANCELLED);
        assertNotNull(GatewayResponse.PaymentStatus.EXPIRED);
        assertNotNull(GatewayResponse.PaymentStatus.REFUNDED);
        assertNotNull(GatewayResponse.PaymentStatus.PARTIALLY_REFUNDED);
        assertNotNull(GatewayResponse.PaymentStatus.CHALLENGE);
    }
}
