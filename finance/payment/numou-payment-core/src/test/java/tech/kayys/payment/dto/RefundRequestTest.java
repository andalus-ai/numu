package tech.kayys.payment.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RefundRequest
 */
class RefundRequestTest {

    private RefundRequest request;

    @BeforeEach
    void setUp() {
        request = new RefundRequest();
    }

    @Test
    @DisplayName("Should create request with default values")
    void testDefaultValues() {
        assertNotNull(request);
        assertNull(request.getPaymentId());
        assertNull(request.getTransactionId());
        assertNull(request.getAmount());
        assertNull(request.getReason());
    }

    @Test
    @DisplayName("Should set payment ID")
    void testPaymentId() {
        request.setPaymentId(123L);
        assertEquals(123L, request.getPaymentId());
    }

    @Test
    @DisplayName("Should set transaction ID")
    void testTransactionId() {
        request.setTransactionId("TXN123456");
        assertEquals("TXN123456", request.getTransactionId());
    }

    @Test
    @DisplayName("Should set refund amount")
    void testAmount() {
        request.setAmount(new BigDecimal("50000"));
        assertEquals(new BigDecimal("50000"), request.getAmount());
    }

    @Test
    @DisplayName("Should set reason")
    void testReason() {
        request.setReason("Customer requested refund");
        assertEquals("Customer requested refund", request.getReason());
    }

    @Test
    @DisplayName("Should set notes")
    void testNotes() {
        request.setNotes("Processed by customer service");
        assertEquals("Processed by customer service", request.getNotes());
    }

    @Test
    @DisplayName("Should identify full refund")
    void testFullRefund() {
        request.setAmount(null);
        assertTrue(request.isFullRefund());

        request.setAmount(new BigDecimal("50000"));
        assertFalse(request.isFullRefund());
    }

    @Test
    @DisplayName("Should build complete refund request")
    void testCompleteRequest() {
        request.setPaymentId(456L);
        request.setTransactionId("TXN789");
        request.setAmount(new BigDecimal("100000"));
        request.setReason("Product defective");
        request.setNotes("Customer sent photo evidence");

        assertEquals(456L, request.getPaymentId());
        assertEquals("TXN789", request.getTransactionId());
        assertEquals(new BigDecimal("100000"), request.getAmount());
        assertEquals("Product defective", request.getReason());
        assertEquals("Customer sent photo evidence", request.getNotes());
        assertFalse(request.isFullRefund());
    }
}
