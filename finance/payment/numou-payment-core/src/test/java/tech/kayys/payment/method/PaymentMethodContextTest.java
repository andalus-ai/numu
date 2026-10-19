package tech.kayys.payment.method;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PaymentMethodContext
 */
class PaymentMethodContextTest {

    private PaymentMethodContext context;

    @BeforeEach
    void setUp() {
        context = new PaymentMethodContext();
    }

    @Test
    @DisplayName("Should create context with default values")
    void testDefaultValues() {
        assertNotNull(context);
        assertEquals("IDR", context.getCurrency());
        assertNotNull(context.getMetadata());
        assertTrue(context.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("Should set and get transaction ID")
    void testTransactionId() {
        context.setTransactionId("TXN123456");
        assertEquals("TXN123456", context.getTransactionId());
    }

    @Test
    @DisplayName("Should set and get external order ID")
    void testExternalOrderId() {
        context.setExternalOrderId("ORDER-789");
        assertEquals("ORDER-789", context.getExternalOrderId());
    }

    @Test
    @DisplayName("Should set and get amount")
    void testAmount() {
        BigDecimal amount = new BigDecimal("150000");
        context.setAmount(amount);
        assertEquals(amount, context.getAmount());
    }

    @Test
    @DisplayName("Should set and get payment method")
    void testPaymentMethod() {
        context.setPaymentMethod(PaymentMethodType.QRIS);
        assertEquals(PaymentMethodType.QRIS, context.getPaymentMethod());
    }

    @Test
    @DisplayName("Should set and get gateway provider")
    void testGatewayProvider() {
        context.setGatewayProvider("MIDTRANS");
        assertEquals("MIDTRANS", context.getGatewayProvider());
    }

    @Test
    @DisplayName("Should set customer information")
    void testCustomerInfo() {
        context.setCustomerName("John Doe");
        context.setCustomerEmail("john@example.com");
        context.setCustomerPhone("08123456789");

        assertEquals("John Doe", context.getCustomerName());
        assertEquals("john@example.com", context.getCustomerEmail());
        assertEquals("08123456789", context.getCustomerPhone());
    }

    @Test
    @DisplayName("Should add metadata")
    void testAddMetadata() {
        context.addMetadata("key1", "value1");
        context.addMetadata("key2", 123);

        assertEquals("value1", context.getMetadata().get("key1"));
        assertEquals(123, context.getMetadata().get("key2"));
    }

    @Test
    @DisplayName("Should set metadata map")
    void testSetMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("item", "product");
        metadata.put("quantity", 2);

        context.setMetadata(metadata);

        assertEquals(metadata, context.getMetadata());
        assertEquals("product", context.getMetadata().get("item"));
    }

    @Test
    @DisplayName("Should set callback and return URLs")
    void testUrls() {
        context.setCallbackUrl("https://example.com/callback");
        context.setReturnUrl("https://example.com/return");

        assertEquals("https://example.com/callback", context.getCallbackUrl());
        assertEquals("https://example.com/return", context.getReturnUrl());
    }

    @Test
    @DisplayName("Should build complete context")
    void testCompleteContext() {
        context.setTransactionId("TXN123");
        context.setExternalOrderId("ORDER-456");
        context.setAmount(new BigDecimal("200000"));
        context.setCurrency("IDR");
        context.setPaymentMethod(PaymentMethodType.QRIS);
        context.setGatewayProvider("MIDTRANS");
        context.setCustomerName("Jane Doe");
        context.setCustomerEmail("jane@example.com");
        context.setCustomerPhone("08198765432");
        context.setCallbackUrl("https://example.com/callback");
        context.setReturnUrl("https://example.com/return");
        context.addMetadata("order_type", "online");

        assertEquals("TXN123", context.getTransactionId());
        assertEquals("ORDER-456", context.getExternalOrderId());
        assertEquals(new BigDecimal("200000"), context.getAmount());
        assertEquals(PaymentMethodType.QRIS, context.getPaymentMethod());
        assertEquals("MIDTRANS", context.getGatewayProvider());
        assertEquals("Jane Doe", context.getCustomerName());
        assertEquals("online", context.getMetadata().get("order_type"));
    }
}
