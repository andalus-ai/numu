package tech.kayys.payment.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.kayys.payment.Payment;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Unit tests for CreatePaymentRequest
 */
class CreatePaymentRequestTest {

    private CreatePaymentRequest request;

    @BeforeEach
    void setUp() {
        request = new CreatePaymentRequest();
    }

    @Test
    @DisplayName("Should create request with default values")
    void testDefaultValues() {
        assertNotNull(request);
        assertEquals("IDR", request.getCurrency());
        assertNotNull(request.getMetadata());
        assertTrue(request.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("Should set external order ID")
    void testExternalOrderId() {
        request.setExternalOrderId("ORDER-12345");
        assertEquals("ORDER-12345", request.getExternalOrderId());
    }

    @Test
    @DisplayName("Should set payment method")
    void testPaymentMethod() {
        request.setPaymentMethod("QRIS");
        assertEquals("QRIS", request.getPaymentMethod());
    }

    @Test
    @DisplayName("Should get payment method type from name")
    void testGetPaymentMethodTypeFromName() {
        request.setPaymentMethod("QRIS");
        assertEquals(PaymentMethodType.QRIS, request.getPaymentMethodType());

        request.setPaymentMethod("VA_BCA");
        assertEquals(PaymentMethodType.VA_BCA, request.getPaymentMethodType());
    }

    @Test
    @DisplayName("Should get payment method type from code")
    void testGetPaymentMethodTypeFromCode() {
        request.setPaymentMethod("qris");
        assertEquals(PaymentMethodType.QRIS, request.getPaymentMethodType());

        request.setPaymentMethod("va_bca");
        assertEquals(PaymentMethodType.VA_BCA, request.getPaymentMethodType());
    }

    @Test
    @DisplayName("Should return null for invalid payment method")
    void testInvalidPaymentMethod() {
        request.setPaymentMethod("INVALID_METHOD");
        assertNull(request.getPaymentMethodType());
    }

    @Test
    @DisplayName("Should set gateway provider")
    void testGatewayProvider() {
        request.setGatewayProvider("MIDTRANS");
        assertEquals("MIDTRANS", request.getGatewayProvider());
    }

    @Test
    @DisplayName("Should set amount")
    void testAmount() {
        request.setAmount(new BigDecimal("150000"));
        assertEquals(new BigDecimal("150000"), request.getAmount());
    }

    @Test
    @DisplayName("Should set customer information")
    void testCustomerInfo() {
        request.setCustomerName("John Doe");
        request.setCustomerEmail("john@example.com");
        request.setCustomerPhone("08123456789");

        assertEquals("John Doe", request.getCustomerName());
        assertEquals("john@example.com", request.getCustomerEmail());
        assertEquals("08123456789", request.getCustomerPhone());
    }

    @Test
    @DisplayName("Should set URLs")
    void testUrls() {
        request.setCallbackUrl("https://example.com/callback");
        request.setReturnUrl("https://example.com/return");

        assertEquals("https://example.com/callback", request.getCallbackUrl());
        assertEquals("https://example.com/return", request.getReturnUrl());
    }

    @Test
    @DisplayName("Should set description")
    void testDescription() {
        request.setDescription("Payment for order #123");
        assertEquals("Payment for order #123", request.getDescription());
    }

    @Test
    @DisplayName("Should add metadata")
    void testMetadata() {
        request.addMetadata("order_type", "online");
        request.addMetadata("channel", "mobile");

        assertEquals("online", request.getMetadata().get("order_type"));
        assertEquals("mobile", request.getMetadata().get("channel"));
    }

    @Test
    @DisplayName("Should build complete request")
    void testCompleteRequest() {
        request.setExternalOrderId("ORDER-789");
        request.setPaymentMethod("VA_BCA");
        request.setGatewayProvider("MIDTRANS");
        request.setAmount(new BigDecimal("200000"));
        request.setCurrency("IDR");
        request.setCustomerName("Jane Doe");
        request.setCustomerEmail("jane@example.com");
        request.setCustomerPhone("08198765432");
        request.setDescription("Payment for invoice");
        request.setCallbackUrl("https://example.com/webhook");
        request.setReturnUrl("https://example.com/success");
        request.addMetadata("source", "web");

        assertEquals("ORDER-789", request.getExternalOrderId());
        assertEquals(PaymentMethodType.VA_BCA, request.getPaymentMethodType());
        assertEquals("MIDTRANS", request.getGatewayProvider());
        assertEquals(new BigDecimal("200000"), request.getAmount());
        assertEquals("Jane Doe", request.getCustomerName());
        assertEquals("web", request.getMetadata().get("source"));
    }
}
