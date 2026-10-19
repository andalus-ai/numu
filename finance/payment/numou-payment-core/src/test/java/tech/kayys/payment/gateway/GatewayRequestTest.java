package tech.kayys.payment.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GatewayRequest
 */
class GatewayRequestTest {

    private GatewayRequest request;

    @BeforeEach
    void setUp() {
        request = new GatewayRequest();
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
    @DisplayName("Should set transaction details")
    void testTransactionDetails() {
        request.setTransactionId("TXN123456");
        request.setExternalOrderId("ORDER-789");
        request.setAmount(new BigDecimal("150000"));
        request.setCurrency("IDR");

        assertEquals("TXN123456", request.getTransactionId());
        assertEquals("ORDER-789", request.getExternalOrderId());
        assertEquals(new BigDecimal("150000"), request.getAmount());
        assertEquals("IDR", request.getCurrency());
    }

    @Test
    @DisplayName("Should set payment method")
    void testPaymentMethod() {
        request.setPaymentMethod("QRIS");
        assertEquals("QRIS", request.getPaymentMethod());
    }

    @Test
    @DisplayName("Should set customer information")
    void testCustomerInfo() {
        GatewayRequest.CustomerInfo customer = new GatewayRequest.CustomerInfo();
        customer.setId("CUST-001");
        customer.setName("John Doe");
        customer.setEmail("john@example.com");
        customer.setPhone("08123456789");

        request.setCustomer(customer);

        assertEquals("CUST-001", request.getCustomer().getId());
        assertEquals("John Doe", request.getCustomer().getName());
        assertEquals("john@example.com", request.getCustomer().getEmail());
        assertEquals("08123456789", request.getCustomer().getPhone());
    }

    @Test
    @DisplayName("Should set items")
    void testItems() {
        GatewayRequest.ItemInfo item1 = new GatewayRequest.ItemInfo();
        item1.setId("ITEM-001");
        item1.setName("Product A");
        item1.setPrice(new BigDecimal("50000"));
        item1.setQuantity(2);
        item1.setCategory("Electronics");

        GatewayRequest.ItemInfo item2 = new GatewayRequest.ItemInfo();
        item2.setId("ITEM-002");
        item2.setName("Product B");
        item2.setPrice(new BigDecimal("75000"));
        item2.setQuantity(1);

        request.setItems(new GatewayRequest.ItemInfo[]{item1, item2});

        assertEquals(2, request.getItems().length);
        assertEquals("ITEM-001", request.getItems()[0].getId());
        assertEquals("Product A", request.getItems()[0].getName());
        assertEquals(new BigDecimal("50000"), request.getItems()[0].getPrice());
        assertEquals(2, request.getItems()[0].getQuantity());
    }

    @Test
    @DisplayName("Should set callback and return URLs")
    void testUrls() {
        request.setCallbackUrl("https://example.com/callback");
        request.setReturnUrl("https://example.com/return");

        assertEquals("https://example.com/callback", request.getCallbackUrl());
        assertEquals("https://example.com/return", request.getReturnUrl());
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
    @DisplayName("Should set description")
    void testDescription() {
        request.setDescription("Payment for order #12345");
        assertEquals("Payment for order #12345", request.getDescription());
    }

    @Test
    @DisplayName("Should build complete request")
    void testCompleteRequest() {
        request.setTransactionId("TXN789");
        request.setExternalOrderId("ORDER-999");
        request.setAmount(new BigDecimal("200000"));
        request.setCurrency("IDR");
        request.setPaymentMethod("BCA_VA");
        request.setDescription("Payment for invoice #INV-001");
        request.setCallbackUrl("https://example.com/webhook");
        request.setReturnUrl("https://example.com/success");
        request.addMetadata("source", "web");

        GatewayRequest.CustomerInfo customer = new GatewayRequest.CustomerInfo();
        customer.setName("Jane Doe");
        customer.setEmail("jane@example.com");
        customer.setPhone("08198765432");
        request.setCustomer(customer);

        assertEquals("TXN789", request.getTransactionId());
        assertEquals(new BigDecimal("200000"), request.getAmount());
        assertEquals("BCA_VA", request.getPaymentMethod());
        assertEquals("Jane Doe", request.getCustomer().getName());
        assertEquals("web", request.getMetadata().get("source"));
    }
}
