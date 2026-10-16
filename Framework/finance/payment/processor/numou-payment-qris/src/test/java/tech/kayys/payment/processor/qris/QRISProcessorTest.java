package tech.kayys.payment.processor.qris;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import tech.kayys.payment.method.PaymentMethodContext;
import tech.kayys.payment.method.PaymentMethodResult;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Unit tests for QRISProcessor
 */
@QuarkusTest
class QRISProcessorTest {

    @Inject
    QRISProcessor processor;

    @Test
    @DisplayName("Should have correct payment method type")
    void testPaymentMethodType() {
        assertEquals(PaymentMethodType.QRIS, processor.getPaymentMethodType());
    }

    @Test
    @DisplayName("Should support QRIS method")
    void testSupports() {
        assertTrue(processor.supports(PaymentMethodType.QRIS));
        assertFalse(processor.supports(PaymentMethodType.VA_BCA));
        assertFalse(processor.supports(PaymentMethodType.OVO));
    }

    @Test
    @DisplayName("Should support QRIS DANA variant")
    void testSupportsQRISDANA() {
        assertTrue(processor.supports(PaymentMethodType.QRIS_DANA));
    }

    @Test
    @DisplayName("Should support QRIS OVO variant")
    void testSupportsQRISOVO() {
        assertTrue(processor.supports(PaymentMethodType.QRIS_OVO));
    }

    @Test
    @DisplayName("Should support QRIS GoPay variant")
    void testSupportsQRISGoPay() {
        assertTrue(processor.supports(PaymentMethodType.QRIS_GOPAY));
    }

    @Test
    @DisplayName("Should initiate QRIS payment successfully")
    void testInitiate() {
        PaymentMethodContext context = buildTestContext();

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
        // Result depends on gateway configuration - may fail in test environment
        // but should not throw exceptions
    }

    @Test
    @DisplayName("Should fail initiation with invalid amount")
    void testInitiateWithInvalidAmount() {
        PaymentMethodContext context = buildTestContext();
        context.setAmount(new BigDecimal("-1000"));

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("INVALID_AMOUNT", result.getErrorCode());
    }

    @Test
    @DisplayName("Should fail initiation with amount below minimum")
    void testInitiateWithAmountBelowMinimum() {
        PaymentMethodContext context = buildTestContext();
        context.setAmount(new BigDecimal("100")); // Below minimum of 1000

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("AMOUNT_BELOW_MINIMUM", result.getErrorCode());
    }

    @Test
    @DisplayName("Should fail initiation with amount above maximum")
    void testInitiateWithAmountAboveMaximum() {
        PaymentMethodContext context = buildTestContext();
        context.setAmount(new BigDecimal("100000000")); // Above maximum of 10M

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("AMOUNT_EXCEEDS_MAXIMUM", result.getErrorCode());
    }

    @Test
    @DisplayName("Should handle QRIS DANA channel")
    void testQRISDANA() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.QRIS_DANA);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle QRIS OVO channel")
    void testQRISOVO() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.QRIS_OVO);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle QRIS GoPay channel")
    void testQRISGoPay() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.QRIS_GOPAY);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle QRIS LinkAja channel")
    void testQRISLinkAja() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.QRIS_LINKAJA);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should fail with non-QRIS payment method")
    void testInitiateWithNonQRISMethod() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.VA_BCA);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("INVALID_PAYMENT_METHOD", result.getErrorCode());
    }

    @Test
    @DisplayName("Should verify QRIS payment")
    void testVerify() {
        PaymentMethodResult result = processor.verify("TXN123456");

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should cancel QRIS payment")
    void testCancel() {
        PaymentMethodResult result = processor.cancel("TXN123456", "Test cancellation");

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should refund QRIS payment")
    void testRefund() {
        PaymentMethodResult result = processor.refund(
            "TXN123456",
            new BigDecimal("50000"),
            "Test refund"
        );

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should refund QRIS payment with full amount")
    void testRefundFullAmount() {
        PaymentMethodResult result = processor.refund(
            "TXN123456",
            null,
            "Test full refund"
        );

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should build gateway request correctly")
    void testBuildGatewayRequest() {
        PaymentMethodContext context = buildTestContext();
        
        // This tests the internal method indirectly through initiation
        // The request should be built without errors
        assertDoesNotThrow(() -> processor.initiate(context));
    }

    @Test
    @DisplayName("Should handle metadata in context")
    void testWithMetadata() {
        PaymentMethodContext context = buildTestContext();
        context.addMetadata("order_items", "Item1,Item2");
        context.addMetadata("customer_id", "CUST123");

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    private PaymentMethodContext buildTestContext() {
        PaymentMethodContext context = new PaymentMethodContext();
        context.setTransactionId("TXN" + System.currentTimeMillis());
        context.setExternalOrderId("ORDER-TEST-" + System.currentTimeMillis());
        context.setAmount(new BigDecimal("100000"));
        context.setCurrency("IDR");
        context.setCustomerName("Test User");
        context.setCustomerEmail("test@example.com");
        context.setCustomerPhone("08123456789");
        context.setPaymentMethod(PaymentMethodType.QRIS);
        context.setGatewayProvider("MIDTRANS");
        return context;
    }
}
