package tech.kayys.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.payment.dto.CreatePaymentRequest;
import tech.kayys.payment.dto.PaymentResponse;
import tech.kayys.payment.dto.RefundRequest;
import tech.kayys.payment.dto.RefundResponse;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Integration tests for Payment Service
 */
@QuarkusTest
class PaymentServiceTest {

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentRepository paymentRepository;

    private Long testPaymentId;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data
        PaymentRefund.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create payment")
    @Transactional
    void testCreatePayment() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-001");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("100000"));
        request.setCustomerName("Test User");
        request.setCustomerEmail("test@example.com");

        Payment payment = paymentService.createPayment(request);

        assertNotNull(payment);
        assertNotNull(payment.id);
        assertEquals(PaymentMethodType.QRIS, payment.paymentMethod);
        assertEquals(new BigDecimal("100000"), payment.amount);
        assertEquals("Test User", payment.customerName);
    }

    @Test
    @DisplayName("Should get payment by ID")
    @Transactional
    void testGetPaymentById() {
        // Create payment first
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-002");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("50000"));
        Payment payment = paymentService.createPayment(request);

        // Retrieve payment
        Payment retrieved = paymentService.getPaymentById(payment.id);

        assertNotNull(retrieved);
        assertEquals(payment.id, retrieved.id);
        assertEquals("ORDER-TEST-002", retrieved.externalOrderId);
    }

    @Test
    @DisplayName("Should throw exception for non-existent payment")
    @Transactional
    void testGetNonExistentPayment() {
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            paymentService.getPaymentById(999999L);
        });
    }

    @Test
    @DisplayName("Should get payment by transaction ID")
    @Transactional
    void testGetPaymentByTransactionId() {
        // Create payment
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-003");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("75000"));
        Payment payment = paymentService.createPayment(request);

        // Retrieve by transaction ID
        Payment retrieved = paymentService.getPaymentByTransactionId(payment.transactionId);

        assertNotNull(retrieved);
        assertEquals(payment.transactionId, retrieved.transactionId);
    }

    @Test
    @DisplayName("Should get payments by invoice ID")
    @Transactional
    void testGetPaymentsByInvoiceId() {
        // Create payments
        CreatePaymentRequest request1 = new CreatePaymentRequest();
        request1.setExternalOrderId("INV-001");
        request1.setPaymentMethod("QRIS");
        request1.setAmount(new BigDecimal("100000"));
        paymentService.createPayment(request1);

        CreatePaymentRequest request2 = new CreatePaymentRequest();
        request2.setExternalOrderId("INV-001");
        request2.setPaymentMethod("VA_BCA");
        request2.setAmount(new BigDecimal("150000"));
        paymentService.createPayment(request2);

        // Get payments by invoice
        // Note: This depends on Invoice entity relationship
        var payments = paymentService.getPaymentsByInvoiceId(1L);
        assertNotNull(payments);
    }

    @Test
    @DisplayName("Should get pending payments")
    @Transactional
    void testGetPendingPayments() {
        // Create payment (will be in PENDING status)
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-004");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("200000"));
        paymentService.createPayment(request);

        var pendingPayments = paymentService.getPendingPayments();
        assertNotNull(pendingPayments);
    }

    @Test
    @DisplayName("Should create and process payment")
    @Transactional
    void testCreateAndProcessPayment() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-005");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("100000"));
        request.setCustomerName("Test User");
        request.setCustomerEmail("test@example.com");
        request.setCustomerPhone("08123456789");

        PaymentResponse response = paymentService.createAndProcessPayment(request);

        assertNotNull(response);
        assertNotNull(response.getTransactionId());
        assertEquals("QRIS", response.getPaymentMethod());
        // Status depends on gateway configuration
        assertNotNull(response.getStatus());
    }

    @Test
    @DisplayName("Should verify payment")
    @Transactional
    void testVerifyPayment() {
        // Create payment
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-006");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("50000"));
        Payment payment = paymentService.createPayment(request);

        // Verify payment
        Payment verified = paymentService.verifyPayment(payment.id);

        assertNotNull(verified);
        // Status may change based on gateway response
        assertNotNull(verified.status);
    }

    @Test
    @DisplayName("Should cancel payment")
    @Transactional
    void testCancelPayment() {
        // Create payment
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-007");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("75000"));
        Payment payment = paymentService.createPayment(request);

        // Cancel payment
        Payment cancelled = paymentService.cancelPayment(payment.id, "Test cancellation");

        assertNotNull(cancelled);
        // Status depends on gateway support
        assertNotNull(cancelled.status);
    }

    @Test
    @DisplayName("Should throw exception for cancelling completed payment")
    @Transactional
    void testCancelCompletedPayment() {
        // Create payment
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-008");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("100000"));
        Payment payment = paymentService.createPayment(request);

        // Manually set to completed for testing
        payment.status = Payment.PaymentStatus.COMPLETED;
        paymentRepository.persist(payment);

        // Should throw exception
        assertThrows(IllegalStateException.class, () -> {
            paymentService.cancelPayment(payment.id, "Cannot cancel");
        });
    }

    @Test
    @DisplayName("Should create refund request")
    @Transactional
    void testRefundRequest() {
        // Create payment
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-009");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("100000"));
        Payment payment = paymentService.createPayment(request);
        payment.status = Payment.PaymentStatus.COMPLETED;
        paymentRepository.persist(payment);

        // Create refund request
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setAmount(new BigDecimal("50000"));
        refundRequest.setReason("Product return");

        RefundResponse response = paymentService.refundPayment(payment.id, refundRequest);

        assertNotNull(response);
        assertNotNull(response.getRefundId());
        // Status depends on gateway configuration
        assertNotNull(response.getStatus());
    }

    @Test
    @DisplayName("Should throw exception for refunding non-existent payment")
    @Transactional
    void testRefundNonExistentPayment() {
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setAmount(new BigDecimal("50000"));

        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            paymentService.refundPayment(999999L, refundRequest);
        });
    }

    @Test
    @DisplayName("Should handle full refund")
    @Transactional
    void testFullRefund() {
        // Create payment
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-010");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("100000"));
        Payment payment = paymentService.createPayment(request);
        payment.status = Payment.PaymentStatus.COMPLETED;
        paymentRepository.persist(payment);

        // Create full refund request (no amount specified)
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setReason("Full refund");

        RefundResponse response = paymentService.refundPayment(payment.id, refundRequest);

        assertNotNull(response);
        assertEquals(payment.amount, response.getAmount());
    }

    @Test
    @DisplayName("Should get payments by status")
    @Transactional
    void testGetPaymentsByStatus() {
        // Create payments with different statuses
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-011");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("100000"));
        paymentService.createPayment(request);

        var pendingPayments = paymentService.getPaymentsByStatus(Payment.PaymentStatus.PENDING);
        assertNotNull(pendingPayments);
    }

    @Test
    @DisplayName("Payment entity should have correct initial status")
    @Transactional
    void testPaymentInitialStatus() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-012");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("50000"));

        Payment payment = paymentService.createPayment(request);

        assertEquals(Payment.PaymentStatus.PENDING, payment.status);
    }

    @Test
    @DisplayName("Payment entity should generate transaction ID")
    @Transactional
    void testTransactionIdGeneration() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-013");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("50000"));

        Payment payment = paymentService.createPayment(request);

        assertNotNull(payment.transactionId);
        assertTrue(payment.transactionId.startsWith("TXN"));
    }

    @Test
    @DisplayName("Payment entity should store metadata")
    @Transactional
    void testPaymentMetadata() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setExternalOrderId("ORDER-TEST-014");
        request.setPaymentMethod("QRIS");
        request.setAmount(new BigDecimal("50000"));
        request.addMetadata("order_type", "online");
        request.addMetadata("channel", "mobile");

        Payment payment = paymentService.createPayment(request);

        assertNotNull(payment.metadata);
        assertEquals("online", payment.getMetadata("order_type"));
        assertEquals("mobile", payment.getMetadata("channel"));
    }
}
