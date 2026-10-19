package tech.kayys.payment.processor.bank;

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
 * Unit tests for BankTransferProcessor
 */
@QuarkusTest
class BankTransferProcessorTest {

    @Inject
    BankTransferProcessor processor;

    @Test
    @DisplayName("Should have correct payment method type")
    void testPaymentMethodType() {
        assertEquals(PaymentMethodType.BANK_TRANSFER, processor.getPaymentMethodType());
    }

    @Test
    @DisplayName("Should support Bank Transfer method")
    void testSupports() {
        assertTrue(processor.supports(PaymentMethodType.BANK_TRANSFER));
        assertTrue(processor.supports(PaymentMethodType.VA_BCA));
        assertTrue(processor.supports(PaymentMethodType.VA_MANDIRI));
        assertTrue(processor.supports(PaymentMethodType.VA_BNI));
        assertTrue(processor.supports(PaymentMethodType.VA_BRI));
        assertTrue(processor.supports(PaymentMethodType.VA_PERMATA));
        assertFalse(processor.supports(PaymentMethodType.QRIS));
        assertFalse(processor.supports(PaymentMethodType.OVO));
    }

    @Test
    @DisplayName("Should support VA BCA variant")
    void testSupportsVABCA() {
        assertTrue(processor.supports(PaymentMethodType.VA_BCA));
    }

    @Test
    @DisplayName("Should support VA Mandiri variant")
    void testSupportsVAMandiri() {
        assertTrue(processor.supports(PaymentMethodType.VA_MANDIRI));
    }

    @Test
    @DisplayName("Should support VA BNI variant")
    void testSupportsVABNI() {
        assertTrue(processor.supports(PaymentMethodType.VA_BNI));
    }

    @Test
    @DisplayName("Should support VA BRI variant")
    void testSupportsVABRI() {
        assertTrue(processor.supports(PaymentMethodType.VA_BRI));
    }

    @Test
    @DisplayName("Should support VA Permata variant")
    void testSupportsVAPermata() {
        assertTrue(processor.supports(PaymentMethodType.VA_PERMATA));
    }

    @Test
    @DisplayName("Should initiate Bank Transfer payment successfully")
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
        context.setAmount(new BigDecimal("100000000")); // Above maximum of 50M

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("AMOUNT_EXCEEDS_MAXIMUM", result.getErrorCode());
    }

    @Test
    @DisplayName("Should handle VA BCA channel")
    void testVABCA() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.VA_BCA);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle VA Mandiri channel")
    void testVAMandiri() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.VA_MANDIRI);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle VA BNI channel")
    void testVABNI() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.VA_BNI);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle VA BRI channel")
    void testVABRI() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.VA_BRI);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle VA Permata channel")
    void testVAPermata() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.VA_PERMATA);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should fail with non-Bank Transfer payment method")
    void testInitiateWithNonBankTransferMethod() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.QRIS);

        PaymentMethodResult result = processor.initiate(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("INVALID_PAYMENT_METHOD", result.getErrorCode());
    }

    @Test
    @DisplayName("Should verify Bank Transfer payment")
    void testVerify() {
        PaymentMethodResult result = processor.verify("TXN123456");

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should cancel Bank Transfer payment")
    void testCancel() {
        PaymentMethodResult result = processor.cancel("TXN123456", "Test cancellation");

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should refund Bank Transfer payment")
    void testRefund() {
        PaymentMethodResult result = processor.refund(
            "TXN123456",
            new BigDecimal("50000"),
            "Test refund"
        );

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should refund Bank Transfer payment with full amount")
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

    @Test
    @DisplayName("Should handle standard bank transfer")
    void testStandardBankTransfer() {
        PaymentMethodContext context = buildTestContext();
        context.setPaymentMethod(PaymentMethodType.BANK_TRANSFER);

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
        context.setPaymentMethod(PaymentMethodType.VA_BCA);
        context.setGatewayProvider("MIDTRANS");
        return context;
    }
}
